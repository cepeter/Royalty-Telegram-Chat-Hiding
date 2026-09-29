package io.github.cepeter.royalty.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Bounded v1 binary envelope; header is authenticated as GCM additional data. */
public final class BackupCodec {
    public static final int MAX_BYTES = 1024 * 1024;
    private static final byte[] MAGIC = {'R','Y','B','K'};
    private static final int HEADER = 4 + 1 + 4 + 16 + 12;
    private static final int TAG = 16;
    private static final int ITERATIONS = 600_000;
    private BackupCodec() { }

    public static byte[] encrypt(BackupData data, char[] passphrase) {
        if (passphrase == null || passphrase.length < 12) throw new IllegalArgumentException("passphrase too short");
        byte[] plain = encodePayload(data);
        byte[] salt = new byte[16], nonce = new byte[12], key = null;
        new SecureRandom().nextBytes(salt); new SecureRandom().nextBytes(nonce);
        int total = HEADER + plain.length + TAG;
        if (total > MAX_BYTES) throw new IllegalArgumentException("backup too large");
        byte[] header = new byte[HEADER];
        System.arraycopy(MAGIC, 0, header, 0, MAGIC.length);
        header[4] = 1;
        putInt(header, 5, plain.length + TAG);
        System.arraycopy(salt, 0, header, 9, 16);
        System.arraycopy(nonce, 0, header, 25, 12);
        try {
            key = derive(passphrase, salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(header);
            byte[] encrypted = cipher.doFinal(plain);
            byte[] result = Arrays.copyOf(header, total);
            System.arraycopy(encrypted, 0, result, HEADER, encrypted.length);
            return result;
        } catch (GeneralSecurityException error) { throw new IllegalStateException("backup encryption unavailable", error); }
        finally { Arrays.fill(plain, (byte) 0); if (key != null) Arrays.fill(key, (byte) 0); }
    }

    public static BackupData decrypt(byte[] envelope, char[] passphrase) {
        if (envelope == null || envelope.length < HEADER + TAG + 13 || envelope.length > MAX_BYTES
                || passphrase == null || passphrase.length == 0)
            throw new IllegalArgumentException("invalid backup size or passphrase");
        for (int i = 0; i < MAGIC.length; i++) if (envelope[i] != MAGIC[i]) throw new IllegalArgumentException("unknown backup format");
        if (envelope[4] != 1) throw new IllegalArgumentException("unsupported backup version");
        int cipherSize = getInt(envelope, 5);
        if (cipherSize < TAG + 13 || cipherSize != envelope.length - HEADER)
            throw new IllegalArgumentException("invalid backup length");
        byte[] salt = Arrays.copyOfRange(envelope, 9, 25);
        byte[] nonce = Arrays.copyOfRange(envelope, 25, HEADER);
        byte[] key = null, plain = null;
        try {
            key = derive(passphrase, salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(envelope, 0, HEADER);
            plain = cipher.doFinal(envelope, HEADER, cipherSize);
            return decodePayload(plain);
        } catch (GeneralSecurityException error) { throw new IllegalArgumentException("wrong passphrase or damaged backup", error); }
        finally { if (key != null) Arrays.fill(key, (byte) 0); if (plain != null) Arrays.fill(plain, (byte) 0); }
    }

    private static byte[] derive(char[] passphrase, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, ITERATIONS, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }

    static byte[] encodePayload(BackupData data) {
        if (data == null) throw new IllegalArgumentException("missing backup data");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(1);
            out.writeByte(data.notifications() ? 1 : 0);
            out.writeByte(data.premium() ? 1 : 0);
            out.writeByte(data.background() ? 1 : 0);
            out.writeByte(data.screenOff() ? 1 : 0);
            out.writeInt(data.timeout());
            out.writeInt(data.entries().size());
            for (BackupData.Entry entry : data.entries()) { out.writeLong(entry.ownerId()); out.writeLong(entry.dialogId()); }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    static BackupData decodePayload(byte[] plain) {
        if (plain == null || plain.length < 13 || plain.length > MAX_BYTES - HEADER - TAG)
            throw new IllegalArgumentException("invalid payload size");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(plain));
            if (in.readUnsignedByte() != 1) throw new IllegalArgumentException("unsupported payload version");
            boolean notifications = readBoolean(in), premium = readBoolean(in), background = readBoolean(in), screenOff = readBoolean(in);
            int timeout = in.readInt(), count = in.readInt();
            if (count < 0 || count > BackupData.MAX_ENTRIES || in.available() != count * 16)
                throw new IllegalArgumentException("invalid entry count or trailing bytes");
            List<BackupData.Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) entries.add(new BackupData.Entry(in.readLong(), in.readLong()));
            return new BackupData(entries, notifications, premium, background, screenOff, timeout);
        } catch (IOException error) { throw new IllegalArgumentException("truncated payload", error); }
    }
    private static boolean readBoolean(DataInputStream in) throws IOException {
        int value = in.readUnsignedByte();
        if (value > 1) throw new IllegalArgumentException("invalid boolean");
        return value == 1;
    }
    private static void putInt(byte[] bytes, int index, int value) {
        bytes[index] = (byte)(value >>> 24); bytes[index + 1] = (byte)(value >>> 16);
        bytes[index + 2] = (byte)(value >>> 8); bytes[index + 3] = (byte)value;
    }
    private static int getInt(byte[] bytes, int index) {
        return (bytes[index] & 255) << 24 | (bytes[index + 1] & 255) << 16
                | (bytes[index + 2] & 255) << 8 | bytes[index + 3] & 255;
    }
}
