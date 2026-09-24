package io.github.cepeter.royalty.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DialogKeyTest {
    @Test
    public void roundTripsSignedTelegramIds() {
        DialogKey key = DialogKey.parse("2:-1001234567890");

        assertEquals(2, key.account());
        assertEquals(-1001234567890L, key.dialogId());
        assertEquals("2:-1001234567890", key.toString());
        assertEquals(key, DialogKey.of(2, -1001234567890L));
    }

    @Test
    public void rejectsNonCanonicalOrOutOfRangeKeys() {
        assertThrows(IllegalArgumentException.class, () -> DialogKey.parse(""));
        assertThrows(IllegalArgumentException.class, () -> DialogKey.parse("1"));
        assertThrows(IllegalArgumentException.class, () -> DialogKey.parse("-1:5"));
        assertThrows(IllegalArgumentException.class, () -> DialogKey.parse("16:5"));
        assertThrows(IllegalArgumentException.class, () -> DialogKey.parse("1:0"));
        assertThrows(IllegalArgumentException.class, () -> DialogKey.parse("1: 5"));
        assertThrows(IllegalArgumentException.class, () -> DialogKey.parse("1:9223372036854775808"));
    }

    @Test
    public void equalityIncludesAccount() {
        assertNotEquals(DialogKey.of(0, 42), DialogKey.of(1, 42));
        assertTrue(DialogKey.tryParse("0:42").isPresent());
        assertFalse(DialogKey.tryParse("bad").isPresent());
    }
}
