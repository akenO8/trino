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
package io.trino.operator.aggregation.arrayagg;

import io.trino.spi.block.Block;
import io.trino.spi.block.BlockBuilder;
import io.trino.spi.block.ValueBlock;
import io.trino.spi.function.AccumulatorState;
import io.trino.spi.function.AccumulatorStateMetadata;

@AccumulatorStateMetadata(
        stateFactoryClass = ArrayAggregationStateFactory.class,
        stateSerializerClass = ArrayAggregationStateSerializer.class,
        typeParameters = "T",
        serializedType = "ARRAY(T)")
public interface ArrayAggregationState
        extends AccumulatorState
{
    void add(ValueBlock block, int position);

    void writeAll(BlockBuilder blockBuilder);

    boolean isEmpty();

    default void merge(ArrayAggregationState otherState)
    {
        Block block = ((SingleArrayAggregationState) otherState).removeTempDeserializeBlock();
        ValueBlock valueBlock = block.getUnderlyingValueBlock();
        for (int position = 0; position < block.getPositionCount(); position++) {
            add(valueBlock, block.getUnderlyingValuePosition(position));
        }
    }
}
