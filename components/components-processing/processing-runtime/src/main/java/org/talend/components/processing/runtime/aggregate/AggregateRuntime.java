package org.talend.components.processing.runtime.aggregate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.avro.generic.IndexedRecord;
import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.beam.sdk.coders.BooleanCoder;
import org.apache.beam.sdk.coders.KvCoder;
import org.apache.beam.sdk.transforms.Combine;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.SerializableFunction;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.talend.components.adapter.beam.coders.LazyAvroCoder;
import org.talend.components.adapter.beam.kv.ExtractKVFn;
import org.talend.components.adapter.beam.kv.MergeKVFn;
import org.talend.components.api.component.runtime.RuntimableRuntime;
import org.talend.components.api.container.RuntimeContainer;
import org.talend.components.processing.definition.aggregate.AggregateGroupByProperties;
import org.talend.components.processing.definition.aggregate.AggregateOperationProperties;
import org.talend.components.processing.definition.aggregate.AggregateProperties;
import org.talend.daikon.avro.AvroUtils;
import org.talend.daikon.properties.ValidationResult;

public class AggregateRuntime extends PTransform<PCollection<IndexedRecord>, PCollection<IndexedRecord>>
        implements RuntimableRuntime<AggregateProperties> {

    private AggregateProperties properties;

    private Set<String> groupByFieldPathList = new LinkedHashSet<>();

    private Set<String> operationFieldPathList = new LinkedHashSet<>();

    @Override
    public PCollection<IndexedRecord> expand(PCollection<IndexedRecord> indexedRecordPCollection) {
        // Return an empty result if there are no operations in the list. This is normally not a permitted operation.
        if (operationFieldPathList.size() == 0)
            return (PCollection<IndexedRecord>) (PCollection) indexedRecordPCollection.getPipeline()
                    .apply(Create.empty(AvroCoder.of(AvroUtils.createEmptySchema())));

        PCollection<KV<IndexedRecord, IndexedRecord>> kv = indexedRecordPCollection
                .apply(ParDo.of(new ExtractKVFn(new ArrayList<>(groupByFieldPathList), new ArrayList<>(operationFieldPathList))))
                .setCoder(KvCoder.of(LazyAvroCoder.of(), LazyAvroCoder.of()));

        PCollection<KV<IndexedRecord, KV<Boolean, IndexedRecord>>> allAggregateResult = kv
                .apply(Combine
                        .<IndexedRecord, IndexedRecord, KV<Boolean, IndexedRecord>> perKey(new AggregateCombineFn(properties)))
                .setCoder(KvCoder.of(LazyAvroCoder.of(), KvCoder.of(BooleanCoder.of(), LazyAvroCoder.of())));//

        PCollection<KV<IndexedRecord, IndexedRecord>> aggregateResult = allAggregateResult //
                .apply(ParDo.of(new OnlyValidValues())).setCoder(KvCoder.of(LazyAvroCoder.of(), LazyAvroCoder.of()));

        PCollection<IndexedRecord> result = aggregateResult //
                .apply(ParDo.of(new MergeKVFn())).setCoder(LazyAvroCoder.of());

        return result;
    }

    @Override
    public ValidationResult initialize(RuntimeContainer container, AggregateProperties properties) {
        this.properties = properties;
        for (AggregateGroupByProperties groupProps : properties.filteredGroupBy()) {
            groupByFieldPathList.add(groupProps.fieldPath.getValue());
        }
        for (AggregateOperationProperties funcProps : properties.filteredOperations()) {
            operationFieldPathList.add(funcProps.fieldPath.getValue());
        }

        return ValidationResult.OK;
    }

    private static class OnlyValidValues
            extends DoFn<KV<IndexedRecord, KV<Boolean, IndexedRecord>>, KV<IndexedRecord, IndexedRecord>> {

        @ProcessElement
        public void processElement(ProcessContext c) {
            if (c.element().getValue().getKey())
                c.output(KV.of(c.element().getKey(), c.element().getValue().getValue()));
        }
    }

    private static class OnlyValidValuesFilter
            implements SerializableFunction<KV<IndexedRecord, KV<Boolean, IndexedRecord>>, Boolean> {

        @Override
        public Boolean apply(KV<IndexedRecord, KV<Boolean, IndexedRecord>> input) {
            return input.getValue() == null ? false : input.getValue().getKey();
        }
    }
}
