package org.appwork.utils;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public abstract class WeakObjectCache<T> {
    protected static class Entry<T> extends WeakReference<T> {
        final int hash;
        Entry<T>  next;

        Entry(T value, int hash, Entry<T> next, ReferenceQueue<T> queue) {
            super(value, queue);
            this.hash = hash;
            this.next = next;
        }
    }

    protected Entry<T>[]              table;
    protected int                     size;
    protected int                     threshold;
    protected final ReferenceQueue<T> queue       = new ReferenceQueue<T>();
    protected static final float      LOAD_FACTOR = 0.75f;

    @SuppressWarnings("unchecked")
    public WeakObjectCache(int initialCapacity) {
        int capacity = 1;
        while (capacity < initialCapacity) {
            capacity <<= 1; // Sicherstellen, dass es Power-of-Two ist
        }
        this.table = new Entry[capacity];
        this.threshold = (int) (capacity * WeakObjectCache.LOAD_FACTOR);
    }

    public T get(T value) {
        return this.get(value, false);
    }

    private final T get(final T value, final boolean putFlag) {
        if (value == null) {
            return null;
        }
        this.expungeStaleEntries();
        final int h = this.hash(value);
        int idx = this.table.length - 1 & h;
        Entry<T> prev = null;
        Entry<T> e = this.table[idx];
        while (e != null) {
            final T candidate = e.get();
            if (candidate == null) {
                // On-the-fly cleanup: Wir haben eine tote Referenz gefunden
                this.size--;
                if (prev == null) {
                    this.table[idx] = e.next;
                } else {
                    prev.next = e.next;
                }
            } else if (e.hash == h && this.equalsObject(candidate, value)) {
                // Gefunden! Wir geben die gecachte Instanz zurück
                return candidate;
            } else {
                prev = e;
            }
            e = prev == null ? this.table[idx] : prev.next;
        }
        if (!putFlag) {
            return null;
        }
        // 3. Nicht gefunden: Neu anlegen
        final Entry<T> newEntry = newEntry(value, h, this.table[idx]);
        this.table[idx] = newEntry;
        if (++this.size >= this.threshold) {
            this.resize();
        }
        return value;
    }

    protected Entry<T> newEntry(final T value, final int hash, final Entry<T> next) {
        return new Entry<T>(value, hash, next, this.queue);
    }

    public boolean remove(T value) {
        if (value == null) {
            return false;
        }
        // Vorher aufräumen, um unnötige Suchen in toten Referenzen zu vermeiden
        expungeStaleEntries();
        final int h = hash(value);
        final int idx = (table.length - 1) & h;
        Entry<T> prev = null;
        Entry<T> current = table[idx];
        while (current != null) {
            T candidate = current.get();
            // Wenn das Objekt im Knoten ohnehin schon vom GC gelöscht wurde
            if (candidate == null) {
                if (prev == null) {
                    table[idx] = current.next;
                } else {
                    prev.next = current.next;
                }
                size--;
            }
            // Treffer: Objekt existiert noch und entspricht unserem Suchelement
            else if (current.hash == h && equalsObject(candidate, value)) {
                if (prev == null) {
                    table[idx] = current.next;
                } else {
                    prev.next = current.next;
                }
                size--;
                return true;
            } else {
                prev = current;
            }
            current = (prev == null) ? table[idx] : prev.next;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public T[] toArray(T[] a) {
        expungeStaleEntries();
        // 1. In einer temporären Liste sammeln, da während des Durchlaufs
        // Objekte vom GC gelöscht werden könnten (Größe ist dynamisch).
        final List<T> list = new ArrayList<T>(size);
        for (Entry<T> entry : table) {
            Entry<T> current = entry;
            while (current != null) {
                final T value = current.get();
                if (value != null) {
                    list.add(value);
                }
                current = current.next;
            }
        }
        // 2. Das Ziel-Array in richtiger Größe bereitstellen/befüllen
        if (a.length < list.size()) {
            a = (T[]) Array.newInstance(a.getClass().getComponentType(), list.size());
        }
        return list.toArray(a);
    }

    public T getOrPut(T value) {
        return this.get(value, true);
    }

    protected abstract boolean equalsObject(T a, T b);

    protected abstract int hashCodeObject(T a);

    protected int hash(T key) {
        int h = this.hashCodeObject(key);
        return h ^ h >>> 16;
    }

    @SuppressWarnings("unchecked")
    protected void expungeStaleEntries() {
        Entry<T> item;
        while ((item = (Entry<T>) this.queue.poll()) != null) {
            int idx = this.table.length - 1 & item.hash;
            Entry<T> prev = null;
            Entry<T> current = this.table[idx];
            while (current != null) {
                if (current == item) {
                    if (prev == null) {
                        this.table[idx] = current.next;
                    } else {
                        prev.next = current.next;
                    }
                    this.size--;
                    break;
                }
                prev = current;
                current = current.next;
            }
        }
    }

    protected static final int MAXIMUM_CAPACITY = 1 << 30; // 1.073.741.824

    private void resize() {
        Entry<T>[] oldTable = this.table;
        int oldCap = oldTable.length;
        if (oldCap == WeakObjectCache.MAXIMUM_CAPACITY) {
            // Wir können nicht weiter wachsen, also setzen wir das Limit
            // so hoch, dass resize() nie wieder getriggert wird.
            this.threshold = Integer.MAX_VALUE;
            return;
        }
        // Sicherheitsschalter gegen Überlauf
        int newCap = oldCap << 1;
        if (newCap < 0 || newCap > WeakObjectCache.MAXIMUM_CAPACITY) {
            newCap = WeakObjectCache.MAXIMUM_CAPACITY;
        }
        @SuppressWarnings("unchecked")
        Entry<T>[] newTable = new Entry[newCap];
        // Daten umziehen
        for (int i = 0; i < oldCap; i++) {
            Entry<T> e = oldTable[i];
            while (e != null) {
                Entry<T> next = e.next;
                int idx = newCap - 1 & e.hash;
                e.next = newTable[idx];
                newTable[idx] = e;
                e = next;
            }
        }
        this.table = newTable;
        // Neuen Schwellenwert berechnen, aber nicht über MAX_VALUE
        long newThreshold = (long) (newCap * WeakObjectCache.LOAD_FACTOR);
        this.threshold = (int) Math.min(newThreshold, WeakObjectCache.MAXIMUM_CAPACITY + 1);
    }

    public int size() {
        this.expungeStaleEntries();
        return this.size;
    }
}