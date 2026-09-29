package io.github.cepeter.royalty.core;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public final class BackupTest {
    private static final char[] PASS = "correct horse battery staple".toCharArray();
    private static final DialogKey CHAT = DialogKey.of(0, -42);
    private static BackupData data() {
        HiddenConfig saved = HiddenConfig.fromBindings(Collections.singletonMap(CHAT, 101L),
                Collections.singleton(DialogKey.of(1, 9)), true, true)
                .withPrivacy(true, true, 30000, true);
        return BackupData.fromSaved(saved);
    }
    private static AccountInventory inventory(long first, long second) {
        Map<Integer, AccountInventory.Owner> owners = new HashMap<>();
        if (first > 0) owners.put(0, new AccountInventory.Owner(first, "First"));
        if (second > 0) owners.put(1, new AccountInventory.Owner(second, "Second"));
        return new AccountInventory(owners, true, "");
    }

    @Test public void realCryptoRoundtripRandomnessAndTampering() throws Exception {
        byte[] one = BackupCodec.encrypt(data(), PASS);
        byte[] two = BackupCodec.encrypt(data(), PASS);
        assertFalse(Arrays.equals(one, two));
        assertEquals(data(), BackupCodec.decrypt(one, PASS));
        assertEquals(1, BackupCodec.decrypt(one, PASS).entries().size());
        assertEquals(-42, BackupCodec.decrypt(one, PASS).entries().get(0).dialogId());
        assertReject(one, "wrong password here".toCharArray());
        byte[] tampered = one.clone(); tampered[8] ^= 1; assertReject(tampered, PASS);
        tampered = one.clone(); tampered[tampered.length - 1] ^= 1; assertReject(tampered, PASS);
        assertReject(Arrays.copyOf(one, one.length - 1), PASS);
        tampered = one.clone(); tampered[4] = 2; assertReject(tampered, PASS);
        assertReject(new byte[BackupCodec.MAX_BYTES + 1], PASS);
    }

    @Test public void strictPayloadParserRejectsBadValuesAndDuplicates() throws Exception {
        byte[] valid = BackupCodec.encodePayload(data());
        assertEquals(data(), BackupCodec.decodePayload(valid));
        byte[] bad = valid.clone(); bad[1] = 2; assertPayloadReject(bad);
        bad = valid.clone(); bad[9] = 2; assertPayloadReject(bad);
        bad = valid.clone(); Arrays.fill(bad, 13, 21, (byte) 0); assertPayloadReject(bad);
        bad = valid.clone(); Arrays.fill(bad, 21, 29, (byte) 0); assertPayloadReject(bad);
        assertPayloadReject(Arrays.copyOf(valid, valid.length - 1));
        bad = Arrays.copyOf(valid, valid.length + 1); assertPayloadReject(bad);
        bad = Arrays.copyOf(valid, valid.length + 16); bad[12] = 2;
        System.arraycopy(valid, 13, bad, 29, 16); assertPayloadReject(bad);
    }

    @Test public void exportCountsLegacyUnboundWithoutSerializingThem() {
        HiddenConfig saved = HiddenConfig.fromBindings(Collections.singletonMap(CHAT, 101L),
                Collections.singleton(DialogKey.of(1, 9)), false, false);
        BackupData.Export snapshot = BackupData.exportFromSaved(saved);
        assertEquals(1, snapshot.skippedUnbound());
        assertEquals(1, snapshot.data().entries().size());
        assertEquals(101L, snapshot.data().entries().get(0).ownerId());
        assertEquals(0, BackupData.exportFromSaved(HiddenConfig.empty()).skippedUnbound());
    }

    @Test public void previewMapsStableOwnersPreservesExistingAndAuth() {
        HiddenConfig existing = HiddenConfig.fromBindings(
                Collections.singletonMap(DialogKey.of(0, -42), 999L),
                Collections.singleton(DialogKey.of(1, 7)), false, false)
                .withPrivacy(false, false, 0, true);
        BackupPreview conflict = BackupPreview.create(data(), existing, inventory(101, 999));
        assertEquals(0, conflict.added());
        assertEquals(1, conflict.conflicts());
        assertEquals(Long.valueOf(999), conflict.merged().boundOwner(CHAT));
        assertTrue(conflict.merged().unbound().contains(DialogKey.of(1, 7)));
        assertTrue(conflict.merged().authenticate());
        BackupPreview mapped = BackupPreview.create(data(), HiddenConfig.empty().withPrivacy(false, false, 0, true), inventory(999, 101));
        assertEquals(Long.valueOf(101), mapped.merged().boundOwner(DialogKey.of(1, -42)));
        assertTrue(mapped.merged().authenticate());
        assertTrue(mapped.merged().suppressNotifications());
        assertEquals(1, mapped.added());
        assertEquals(1, BackupPreview.create(data(), existing, inventory(999, 0)).skipped());
        assertEquals(1, BackupPreview.create(data(), existing, inventory(101, 101)).ambiguous());
    }

    @Test public void stalePreviewIsRecomputedAtApplyAndDraftRemainsAdditive() {
        ConfigurationDraft draft = new ConfigurationDraft();
        draft.loadSaved(HiddenConfig.empty().withPrivacy(false, false, 0, true));
        BackupPreview preview = BackupPreview.create(data(), draft.current(), inventory(101, 0));
        assertFalse(preview.apply(draft, inventory(202, 0)));
        assertFalse(draft.current().isHidden(CHAT));
        assertTrue(preview.apply(draft, inventory(101, 0)));
        assertEquals(Long.valueOf(101), draft.current().boundOwner(CHAT));
        assertTrue(draft.current().authenticate());
        assertFalse(draft.canSaveAgainst(inventory(202, 0)));
    }

    private static void assertReject(byte[] bytes, char[] pass) throws Exception {
        try { BackupCodec.decrypt(bytes, pass); fail("accepted invalid envelope"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void assertPayloadReject(byte[] bytes) throws Exception {
        try { BackupCodec.decodePayload(bytes); fail("accepted invalid payload"); }
        catch (IllegalArgumentException expected) { }
    }
}
