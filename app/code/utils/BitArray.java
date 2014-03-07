package code.utils;

import static com.google.common.base.Preconditions.*;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

/**
 * Utility implementation of bit array manipulations, which highly required for rights managment logic.
 */
public final class BitArray {
    private final long[] bits;
    private final int size;

    /**
     * @param size Size of the array.
     */
    public BitArray(int size) {
        checkArgument(size >= 0, "size must be positive");
        this.size = size;
        bits = new long[(size + 63) / 64];
    }

    /**
     * Returns size of the array.  Note: BitArray has fixed size, and don't grow dynamically.
     */
    public int size() {
        return size;
    }

    /**
     * Sets bit with given index to zero or one depending on value.
     */
    public void set(int index, boolean value) {
        checkElementIndex(index, size);
        final long mask = 1L << (index % 64);
        if (value)
            bits[index / 64] |= mask;
        else
            bits[index / 64] &= ~mask;
    }

    /**
     * Returns true, if bit with given index is non-zero.
     */
    public boolean get(int index) {
        checkElementIndex(index, size);
        final long mask = 1L << (index % 64);
        return 0 != (bits[index / 64] & mask);
    }

    /**
     * Performs logical disjunction of this BitArray with given BitArray.  Result comes to this BitArray.
     */
    public void intersect(BitArray b) {
        checkNotNull(b, "b");
        checkArgument(b.size() == this.size, "arrays must be same size");

        for (int i = 0; i < bits.length; i++)
            bits[i] &= b.bits[i];
    }

    /**
     * Performs subtraction of given BitArray from this BitArray.  Result comes to this BitArray.
     */
    public void subtract(BitArray b) {
        checkNotNull(b, "b");
        checkArgument(b.size() == this.size, "arrays must be same size");

        for (int i = 0; i < bits.length; i++)
            bits[i] &= ~b.bits[i];
    }

    /**
     * Performs logical conjunction of this BitArray with given BitArray.  Result comes to this BitArray.
     */
    public void add(BitArray b) {
        checkNotNull(b, "b");
        checkArgument(b.size() == this.size, "arrays must be same size");

        for (int i = 0; i < bits.length; i++)
            bits[i] |= b.bits[i];
    }

    /**
     * Checks that argument BitArray completlyl implicated into this array.
     */
    public boolean isImplicated(BitArray b) {
        checkNotNull(b, "b");
        checkArgument(b.size() == this.size, "arrays must be same size");

        for (int i = 0; i < bits.length; i++)
            if ((bits[i] & b.bits[i]) != b.bits[i])
                return false;
        return true;
    }

    /**
     * Inverse bit array.
     */
    public void inverse() {
        for (int i = 0; i < bits.length; i++)
            bits[i] ^= -1L;
    }

    /**
     * Returns true, masked bits in argument BitArray intersects with bits of this BitArray.
     */
    public boolean isIntersects(BitArray b) {
        checkNotNull(b, "b");
        checkArgument(b.size() == this.size, "arrays must be same size");

        for (int i = 0; i < bits.length; i++)
            if (0 != (bits[i] & b.bits[i]))
                return true;
        return false;
    }

    /**
     * Returns cloned copy of this BitArray.
     */
    public BitArray copy() {
        final BitArray n = new BitArray(size);
        for (int i = 0; i < bits.length; i++)
            n.bits[i] = bits[i];
        return n;
    }

    public boolean isEmpty() {
        int rest = size % 64;
        if (rest == 0) {
            for (int i = 0; i < bits.length; i++)
                if (bits[i] != 0)
                    return false;
            return true;
        } else {
            int end = bits.length - 1;
            for (int i = 0; i < end; i++)
                if (bits[i] != 0)
                    return false;
            return ((bits[end] & ((1L << rest) - 1L)) == 0L);
        }
    }


    /**
     * Returns enumerator of non-zero positions.
     */
    public EnumTrueValues getEnumTrueValues() {
        return new EnumTrueValues();
    }

    /**
     * Return list non-zero (true) elements.
     */
    public String toString() {
        final EnumTrueValues e = getEnumTrueValues();
        final StringBuilder sb = new StringBuilder("[");
        int index;
        boolean isFirst = true;
        while ((index = e.next()) != -1) {
            if (isFirst)
                isFirst = false;
            else
                sb.append(",");
            sb.append(index);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Sets all array elements to false (zero).
     */
    public void clear() {
        for (int i = 0; i < bits.length; i++)
            bits[i] = 0L;
    }

    /**
     * Enumerates true (non-zero) positions within BitArray.
     */
    public final class EnumTrueValues {
        private int index = -1;
        private long mask = 0;
        private int field = -1;

        private EnumTrueValues() {
        }

        /**
         * Returns index of next non-zero event.  -1 at the end.
         */
        public int next() {
            for (index++; index < size; index++) {
                mask <<= 1;
                if (mask == 0) {
                    mask = 1;
                    ++field;
                    while (bits[field] == 0) {
                        if (++field == bits.length)
                            return -1;
                        index += 64;
                    }
                }
                if (0L != (bits[field] & mask))
                    return index;
            }
            return -1;
        }
    }

    @Override
    public int hashCode() {
        long res = bits[0];
        for (int i = 1; i < bits.length; i++)
            res |= bits[i];
        return (int)(res & 0xFFFF) | (int)(res >> 16);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof BitArray))
            return false;
        final BitArray bitArray = (BitArray) obj;
        if (bitArray.bits.length != bits.length)
            return false;
        for (int i = 0; i < bits.length; i++)
            if (bitArray.bits[i] != bits[i])
                return false;
        return true;
    }
}
