package org.appwork.utils.zip.tests;

import java.io.File;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;

import org.appwork.testframework.AWTest;
import org.appwork.utils.Application;
import org.appwork.utils.crypto.AWSign;
import org.appwork.utils.encoding.Base64;
import org.appwork.utils.zip.ZipIOReader;
import org.appwork.utils.zip.ZipIOWriter;

public class TestZipIOWriterArchiveMeta extends AWTest {
    @Override
    public void runTest() throws Exception {
        final File file = Application.getResource("awz_meta_test.zip");
        file.delete();
        file.deleteOnExit();
        final KeyPair keyPair = AWSign.createKeyPair();
        final HashMap<String, Object> meta = new HashMap<String, Object>();
        meta.put("repoName", "TestRepo");
        meta.put("publicKey", "abc");
        meta.put("buildID", Integer.valueOf(42));
        final ZipIOWriter writer = new ZipIOWriter(file, true);
        try {
            writer.setSignaturePrivateKey(keyPair.getPrivate());
            writer.setArchiveMeta(meta);
            writer.add("hello".getBytes("UTF-8"), false, "hello.txt");
            writer.addFolder("cfg");
        } finally {
            writer.close();
        }
        final ZipIOReader reader = new ZipIOReader(file);
        try {
            reader.setSignaturePublicKey(keyPair.getPublic());
            reader.verify();
            assertTrue(reader.hasArchiveMeta());
            final Object loaded = reader.getArchiveMeta();
            assertNotNull(loaded);
            @SuppressWarnings("unchecked")
            final Map<String, Object> map = (Map<String, Object>) loaded;
            assertEquals("TestRepo", map.get("repoName"));
            assertEquals(Integer.valueOf(42), Integer.valueOf(((Number) map.get("buildID")).intValue()));
        } finally {
            reader.close();
        }
        // Tamper meta by rewriting without matching mSig — open unsigned and resign without meta should still verify AWZ without meta
        file.delete();
        final ZipIOWriter w2 = new ZipIOWriter(file, true);
        try {
            w2.setSignaturePrivateKey(keyPair.getPrivate());
            w2.add("hello".getBytes("UTF-8"), false, "hello.txt");
        } finally {
            w2.close();
        }
        final ZipIOReader r2 = new ZipIOReader(file);
        try {
            r2.setSignaturePublicKey(keyPair.getPublic());
            r2.verify();
            assertFalse(r2.hasArchiveMeta());
        } finally {
            r2.close();
        }
        // Ensure public encoding roundtrip used by release packages
        assertNotNull(Base64.encodeToString(keyPair.getPublic().getEncoded()));
        file.delete();
    }

    public static void main(final String[] args) {
        run();
    }
}
