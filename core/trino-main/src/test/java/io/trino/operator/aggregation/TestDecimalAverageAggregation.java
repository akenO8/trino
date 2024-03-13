/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.operator.aggregation;

import com.google.common.collect.ImmutableList;
import io.trino.operator.aggregation.state.LongDecimalWithOverflowAndLongState;
import io.trino.operator.aggregation.state.LongDecimalWithOverflowAndLongStateFactory;
import io.trino.spi.block.BlockBuilder;
import io.trino.spi.block.Int128ArrayBlock;
import io.trino.spi.type.DecimalType;
import io.trino.spi.type.Decimals;
import io.trino.spi.type.Int128;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.List;

import static io.trino.operator.aggregation.DecimalAverageAggregation.average;
import static io.trino.spi.type.DecimalType.createDecimalType;
import static java.math.BigInteger.ONE;
import static java.math.BigInteger.TEN;
import static java.math.BigInteger.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static org.assertj.core.api.Assertions.assertThat;

public class TestDecimalAverageAggregation
{
    private static final BigInteger TWO = new BigInteger("2");
    private static final BigInteger ONE_HUNDRED = new BigInteger("100");
    private static final BigInteger TWO_HUNDRED = new BigInteger("200");
    private static final DecimalType TYPE = createDecimalType(38, 0);

    @Test
    public void testOverflow()
    {
        LongDecimalWithOverflowAndLongState state = new LongDecimalWithOverflowAndLongStateFactory().createSingleState();

        addToState(state, TWO.pow(126));

        assertThat(state.getLong()).isEqualTo(1);
        assertThat(state.getOverflow()).isEqualTo(0);
        assertThat(getDecimal(state)).isEqualTo(Int128.valueOf(TWO.pow(126)));

        addToState(state, TWO.pow(126));

        assertThat(state.getLong()).isEqualTo(2);
        assertThat(state.getOverflow()).isEqualTo(1);
        assertThat(getDecimal(state)).isEqualTo(Int128.valueOf(1L << 63, 0));

        assertAverageEquals(state, TWO.pow(126));
    }

    @Test
    public void testUnderflow()
    {
        LongDecimalWithOverflowAndLongState state = new LongDecimalWithOverflowAndLongStateFactory().createSingleState();

        addToState(state, Decimals.MIN_UNSCALED_DECIMAL.toBigInteger());

        assertThat(state.getLong()).isEqualTo(1);
        assertThat(state.getOverflow()).isEqualTo(0);
        assertThat(getDecimal(state)).isEqualTo(Decimals.MIN_UNSCALED_DECIMAL);

        addToState(state, Decimals.MIN_UNSCALED_DECIMAL.toBigInteger());

        assertThat(state.getLong()).isEqualTo(2);
        assertThat(state.getOverflow()).isEqualTo(-1);
        assertThat(getDecimal(state)).isEqualTo(Int128.valueOf(0x698966AF4AF2770BL, 0xECEBBB8000000002L));

        assertAverageEquals(state, Decimals.MIN_UNSCALED_DECIMAL.toBigInteger());
    }

    @Test
    public void testUnderflowAfterOverflow()
    {
        LongDecimalWithOverflowAndLongState state = new LongDecimalWithOverflowAndLongStateFactory().createSingleState();

        addToState(state, TWO.pow(126));
        addToState(state, TWO.pow(126));
        addToState(state, TWO.pow(125));

        assertThat(state.getOverflow()).isEqualTo(1);
        assertThat(getDecimal(state)).isEqualTo(Int128.valueOf((1L << 63) | (1L << 61), 0));

        addToState(state, TWO.pow(126).negate());
        addToState(state, TWO.pow(126).negate());
        addToState(state, TWO.pow(126).negate());

        assertThat(state.getOverflow()).isEqualTo(0);
        assertThat(getDecimal(state)).isEqualTo(Int128.valueOf(TWO.pow(125).negate()));

        assertAverageEquals(state, TWO.pow(125).negate().divide(BigInteger.valueOf(6)));
    }

    @Test
    public void testCombineOverflow()
    {
        LongDecimalWithOverflowAndLongState state = new LongDecimalWithOverflowAndLongStateFactory().createSingleState();

        addToState(state, TWO.pow(126));
        addToState(state, TWO.pow(126));

        LongDecimalWithOverflowAndLongState otherState = new LongDecimalWithOverflowAndLongStateFactory().createSingleState();

        addToState(otherState, TWO.pow(126));
        addToState(otherState, TWO.pow(126));

        DecimalAverageAggregation.combine(state, otherState);
        assertThat(state.getLong()).isEqualTo(4);
        assertThat(state.getOverflow()).isEqualTo(1);
        assertThat(getDecimal(state)).isEqualTo(Int128.ZERO);

        BigInteger expectedAverage = BigInteger.ZERO
                .add(TWO.pow(126))
                .add(TWO.pow(126))
                .add(TWO.pow(126))
                .add(TWO.pow(126))
                .divide(BigInteger.valueOf(4));

        assertAverageEquals(state, expectedAverage);
    }

    @Test
    public void testCombineUnderflow()
    {
        LongDecimalWithOverflowAndLongState state = new LongDecimalWithOverflowAndLongStateFactory().createSingleState();

        addToState(state, TWO.pow(125).negate());
        addToState(state, TWO.pow(126).negate());

        LongDecimalWithOverflowAndLongState otherState = new LongDecimalWithOverflowAndLongStateFactory().createSingleState();

        addToState(otherState, TWO.pow(125).negate());
        addToState(otherState, TWO.pow(126).negate());

        DecimalAverageAggregation.combine(state, otherState);
        assertThat(state.getLong()).isEqualTo(4);
        assertThat(state.getOverflow()).isEqualTo(-1);
        assertThat(getDecimal(state)).isEqualTo(Int128.valueOf(1L << 62, 0));

        BigInteger expectedAverage = BigInteger.ZERO
                .add(TWO.pow(126))
                .add(TWO.pow(126))
                .add(TWO.pow(125))
                .add(TWO.pow(125))
                .negate()
                .divide(BigInteger.valueOf(4));

        assertAverageEquals(state, expectedAverage);
    }

    @Test
    public void testNoOverflow()
    {
        testNoOverflow(createDecimalType(38, 0), ImmutableList.of(TEN.pow(37), ZERO));
        testNoOverflow(createDecimalType(38, 0), ImmutableList.of(TEN.pow(37).negate(), ZERO));
        testNoOverflow(createDecimalType(38, 0), ImmutableList.of(TWO, ONE));
        testNoOverflow(createDecimalType(38, 0), ImmutableList.of(ZERO, ONE));
        testNoOverflow(createDecimalType(38, 0), ImmutableList.of(TWO.negate(), ONE.negate()));
        testNoOverflow(createDecimalType(38, 0), ImmutableList.of(ONE.negate(), ZERO));
        testNoOverflow(createDecimalType(38, 0), ImmutableList.of(ONE.negate(), ZERO, ZERO));
        testNoOverflow(createDecimalType(38, 0), ImmutableList.of(TWO.negate(), ZERO, ZERO));
        testNoOverflow(createDecimalType(38, 0), ImmutableList.of(TWO.negate(), ZERO));
        testNoOverflow(createDecimalType(38, 0), ImmutableList.of(TWO_HUNDRED, ONE_HUNDRED));
        testNoOverflow(createDecimalType(38, 0), ImmutableList.of(ZERO, ONE_HUNDRED));
        testNoOverflow(createDecimalType(38, 0), ImmutableList.of(TWO_HUNDRED.negate(), ONE_HUNDRED.negate()));
        testNoOverflow(createDecimalType(38, 0), ImmutableList.of(ONE_HUNDRED.negate(), ZERO));

        testNoOverflow(createDecimalType(38, 2), ImmutableList.of(TEN.pow(37), ZERO));
        testNoOverflow(createDecimalType(38, 2), ImmutableList.of(TEN.pow(37).negate(), ZERO));
        testNoOverflow(createDecimalType(38, 2), ImmutableList.of(TWO, ONE));
        testNoOverflow(createDecimalType(38, 2), ImmutableList.of(ZERO, ONE));
        testNoOverflow(createDecimalType(38, 2), ImmutableList.of(TWO.negate(), ONE.negate()));
        testNoOverflow(createDecimalType(38, 2), ImmutableList.of(ONE.negate(), ZERO));
        testNoOverflow(createDecimalType(38, 2), ImmutableList.of(ONE.negate(), ZERO, ZERO));
        testNoOverflow(createDecimalType(38, 2), ImmutableList.of(TWO.negate(), ZERO, ZERO));
        testNoOverflow(createDecimalType(38, 2), ImmutableList.of(TWO.negate(), ZERO));
        testNoOverflow(createDecimalType(38, 2), ImmutableList.of(TWO_HUNDRED, ONE_HUNDRED));
        testNoOverflow(createDecimalType(38, 2), ImmutableList.of(ZERO, ONE_HUNDRED));
        testNoOverflow(createDecimalType(38, 2), ImmutableList.of(TWO_HUNDRED.negate(), ONE_HUNDRED.negate()));
        testNoOverflow(createDecimalType(38, 2), ImmutableList.of(ONE_HUNDRED.negate(), ZERO));
    }

    private void testNoOverflow(DecimalType type, List<BigInteger> numbers)
    {
        LongDecimalWithOverflowAndLongState state = new LongDecimalWithOverflowAndLongStateFactory().createSingleState();
        for (BigInteger number : numbers) {
            addToState(type, state, number);
        }

        assertThat(state.getOverflow()).isEqualTo(0);
        BigInteger sum = numbers.stream().reduce(BigInteger.ZERO, BigInteger::add);
        assertThat(getDecimal(state)).isEqualTo(Int128.valueOf(sum));

        BigDecimal expectedAverage = new BigDecimal(sum, type.getScale()).divide(BigDecimal.valueOf(numbers.size()), type.getScale(), HALF_UP);
        assertThat(decodeBigDecimal(type, average(state, type))).isEqualTo(expectedAverage);
    }

    private static BigDecimal decodeBigDecimal(DecimalType type, Int128 average)
    {
        BigInteger unscaledVal = average.toBigInteger();
        return new BigDecimal(unscaledVal, type.getScale(), new MathContext(type.getPrecision()));
    }

    private void assertAverageEquals(LongDecimalWithOverflowAndLongState state, BigInteger expectedAverage)
    {
        assertThat(average(state, TYPE).toBigInteger()).isEqualTo(expectedAverage);
    }

    private static void addToState(LongDecimalWithOverflowAndLongState state, BigInteger value)
    {
        addToState(TYPE, state, value);
    }

    private static void addToState(DecimalType type, LongDecimalWithOverflowAndLongState state, BigInteger value)
    {
        if (type.isShort()) {
            DecimalAverageAggregation.inputShortDecimal(state, Int128.valueOf(value).toLongExact());
        }
        else {
            BlockBuilder blockBuilder = type.createFixedSizeBlockBuilder(1);
            type.writeObject(blockBuilder, Int128.valueOf(value));
            DecimalAverageAggregation.inputLongDecimal(state, (Int128ArrayBlock) blockBuilder.buildValueBlock(), 0);
        }
    }

    private Int128 getDecimal(LongDecimalWithOverflowAndLongState state)
    {
        long[] decimal = state.getDecimalArray();
        int offset = state.getDecimalArrayOffset();

        return Int128.valueOf(decimal[offset], decimal[offset + 1]);
    }
}
