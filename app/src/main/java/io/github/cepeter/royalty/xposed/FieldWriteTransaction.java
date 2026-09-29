package io.github.cepeter.royalty.xposed;

final class FieldWriteTransaction {
    private FieldWriteTransaction() {}

    interface Writer { void write(Object owner, String field, Object value); }

    static void writePair(Object owner, String firstField, Object firstValue,
            String secondField, Object secondValue, Writer writer) {
        Object originalFirst = ModernHookBridge.getObjectField(owner, firstField);
        Object originalSecond = ModernHookBridge.getObjectField(owner, secondField);
        try {
            writer.write(owner, firstField, firstValue);
            writer.write(owner, secondField, secondValue);
        } catch (RuntimeException | Error failure) {
            restore(owner, secondField, originalSecond, writer, failure);
            restore(owner, firstField, originalFirst, writer, failure);
            throw failure;
        }
    }

    static void writePair(Object owner, String firstField, Object firstValue,
            String secondField, Object secondValue) {
        writePair(owner, firstField, firstValue, secondField, secondValue,
                ModernHookBridge::setObjectField);
    }

    private static void restore(Object owner, String field, Object original,
            Writer writer, Throwable failure) {
        try {
            writer.write(owner, field, original);
        } catch (RuntimeException | Error rollbackError) {
            failure.addSuppressed(rollbackError);
        }
    }
}
