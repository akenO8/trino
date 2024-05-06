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
package io.trino.plugin.deltalake.functions.tablechanges;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableMap;
import io.airlift.slice.SizeOf;
import io.trino.spi.SplitWeight;
import io.trino.spi.connector.ConnectorSplit;

import java.util.Map;
import java.util.Optional;

import static io.airlift.slice.SizeOf.estimatedSizeOf;
import static io.airlift.slice.SizeOf.instanceSize;
import static io.airlift.slice.SizeOf.sizeOf;

public record TableChangesSplit(
        String path,
        long fileSize,
        Map<String, Optional<String>> partitionKeys,
        long currentVersionCommitTimestamp,
        TableChangesFileType fileType,
        long currentVersion)
        implements ConnectorSplit
{
    private static final int INSTANCE_SIZE = instanceSize(TableChangesSplit.class);

    @Override
    public Map<String, String> getSplitInfo()
    {
        return ImmutableMap.<String, String>builder()
                .put("path", path)
                .put("length", String.valueOf(fileSize))
                .buildOrThrow();
    }

    @JsonIgnore
    @Override
    public SplitWeight getSplitWeight()
    {
        return SplitWeight.standard();
    }

    @Override
    public long getRetainedSizeInBytes()
    {
        return INSTANCE_SIZE
                + estimatedSizeOf(path)
                + estimatedSizeOf(partitionKeys, SizeOf::estimatedSizeOf, value -> sizeOf(value, SizeOf::estimatedSizeOf));
    }
}
