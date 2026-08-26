/**
 *
 * ====================================================================================================================================================
 *         "AppWork Utilities" License
 *         The "AppWork Utilities" will be called [The Product] from now on.
 * ====================================================================================================================================================
 *         Copyright (c) 2009-2026, AppWork GmbH <e-mail@appwork.org>
 *         Spalter Strasse 58
 *         91183 Abenberg
 *         Germany
 * === Preamble ===
 *     This license establishes the terms under which the [The Product] Source Code & Binary files may be used, copied, modified, distributed, and/or redistributed.
 *     The intent is that the AppWork GmbH is able to provide  their utilities library for free to non-commercial projects whereas commercial usage is only permitted after obtaining a commercial license.
 *     These terms apply to all files that have the [The Product] License header (IN the file), a <filename>.license or <filename>.info (like mylib.jar.info) file that contains a reference to this license.
 *
 * === 3rd Party Licences ===
 *     Some parts of the [The Product] use or reference 3rd party libraries and classes. These parts may have different licensing conditions. Please check the *.license and *.info files of included libraries
 *     to ensure that they are compatible to your use-case. Further more, some *.java have their own license. In this case, they have their license terms in the java file header.
 *
 * === Definition: Commercial Usage ===
 *     If anybody or any organization is generating income (directly or indirectly) by using [The Product] or if there's any commercial interest or aspect in what you are doing, we consider this as a commercial usage.
 *     If your use-case is neither strictly private nor strictly educational, it is commercial. If you are unsure whether your use-case is commercial or not, consider it as commercial or contact as.
 * === Dual Licensing ===
 * === Commercial Usage ===
 *     If you want to use [The Product] in a commercial way (see definition above), you have to obtain a paid license from AppWork GmbH.
 *     Contact AppWork for further details: e-mail@appwork.org
 * === Non-Commercial Usage ===
 *     If there is no commercial usage (see definition above), you may use [The Product] under the terms of the
 *     "GNU Affero General Public License" (http://www.gnu.org/licenses/agpl-3.0.en.html).
 *
 *     If the AGPL does not fit your needs, please contact us. We'll find a solution.
 * ====================================================================================================================================================
 * ==================================================================================================================================================== */
package org.appwork.testframework;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.appwork.storage.simplejson.mapper.ClassCache;

/**
 * Optional markers a test can declare via {@link TestInterface#getTags()} / {@link PostBuildTestInterface#getTags()}. Runners use these to
 * schedule tests that need user attention (UAC consent, dialogs) before unattended ones.
 *
 * @author thomas
 * @date 04.08.2026
 */
public enum TestTag {
    /**
     * Test may open dialogs or otherwise require a human at the machine (beyond UAC).
     */
    INTERACTIVE,
    /**
     * Test may trigger a Windows UAC consent prompt (ShellExecute runas / elevation).
     */
    UAC;

    /**
     * @param tags
     *            may be null
     * @return true if any tag implies the user may need to interact
     */
    public static boolean requiresUserAttention(final Set<TestTag> tags) {
        return userAttentionRank(tags) > 0;
    }

    /**
     * Higher value means the test should run earlier. UAC ranks above INTERACTIVE-only.
     *
     * @param tags
     *            may be null
     */
    public static int userAttentionRank(final Set<TestTag> tags) {
        if (tags == null || tags.isEmpty()) {
            return 0;
        }
        int rank = 0;
        if (tags.contains(UAC)) {
            rank += 2;
        }
        if (tags.contains(INTERACTIVE)) {
            rank += 1;
        }
        return rank;
    }

    /**
     * @param instance
     *            test instance or null
     * @return non-null tags (empty if unknown / null tags from test)
     */
    public static Set<TestTag> resolveFromInstance(final Object instance) {
        if (instance instanceof TestInterface) {
            final Set<TestTag> tags = ((TestInterface) instance).getTags();
            return tags != null ? tags : Collections.<TestTag> emptySet();
        } else if (instance instanceof PostBuildTestInterface) {
            final Set<TestTag> tags = ((PostBuildTestInterface) instance).getTags();
            return tags != null ? tags : Collections.<TestTag> emptySet();
        } else {
            return Collections.emptySet();
        }
    }

    /**
     * Instantiates (via {@link ClassCache} or no-arg constructor) and reads {@code getTags()}. Failures yield an empty set.
     */
    public static Set<TestTag> resolveFromClass(final Class<?> cls) {
        if (cls == null || cls.isInterface() || Modifier.isAbstract(cls.getModifiers()) || cls.isAnonymousClass()) {
            return Collections.emptySet();
        }
        try {
            Object inst;
            try {
                inst = ClassCache.getClassCache(cls).getInstance();
            } catch (final Throwable ignore) {
                inst = cls.getConstructor(new Class[] {}).newInstance(new Object[] {});
            }
            return resolveFromInstance(inst);
        } catch (final Throwable e) {
            return Collections.emptySet();
        }
    }

    /**
     * Stable-partitions {@code classNames} so UAC/INTERACTIVE tests run first (UAC before INTERACTIVE-only). Relative order within the same
     * rank is preserved.
     */
    public static void moveUserAttentionClassNamesFirst(final List<String> classNames) {
        if (classNames == null || classNames.size() <= 1) {
            return;
        }
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        final HashMap<String, Integer> ranks = new HashMap<String, Integer>();
        for (final String name : classNames) {
            int rank = 0;
            try {
                rank = userAttentionRank(resolveFromClass(Class.forName(name, false, cl)));
            } catch (final Throwable ignore) {
            }
            ranks.put(name, Integer.valueOf(rank));
        }
        partitionByRank(classNames, ranks);
    }

    /**
     * Same as {@link #moveUserAttentionClassNamesFirst(List)} for {@link Class} lists (e.g. PostBuildRunner).
     */
    public static void moveUserAttentionClassesFirst(final List<Class<?>> classes) {
        if (classes == null || classes.size() <= 1) {
            return;
        }
        final HashMap<String, Integer> ranksByName = new HashMap<String, Integer>();
        final ArrayList<String> names = new ArrayList<String>(classes.size());
        final HashMap<String, Class<?>> byName = new HashMap<String, Class<?>>();
        for (final Class<?> cls : classes) {
            final String name = cls.getName();
            names.add(name);
            byName.put(name, cls);
            ranksByName.put(name, Integer.valueOf(userAttentionRank(resolveFromClass(cls))));
        }
        partitionByRank(names, ranksByName);
        classes.clear();
        for (final String name : names) {
            classes.add(byName.get(name));
        }
    }

    private static void partitionByRank(final List<String> names, final HashMap<String, Integer> ranks) {
        final ArrayList<String> attention = new ArrayList<String>();
        final ArrayList<String> others = new ArrayList<String>();
        for (final String name : names) {
            if (ranks.get(name).intValue() > 0) {
                attention.add(name);
            } else {
                others.add(name);
            }
        }
        Collections.sort(attention, new Comparator<String>() {
            @Override
            public int compare(final String a, final String b) {
                return ranks.get(b).intValue() - ranks.get(a).intValue();
            }
        });
        names.clear();
        names.addAll(attention);
        names.addAll(others);
    }
}
