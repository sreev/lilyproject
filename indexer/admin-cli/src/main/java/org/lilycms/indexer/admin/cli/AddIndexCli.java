package org.lilycms.indexer.admin.cli;

import org.apache.commons.cli.*;
import org.lilycms.indexer.model.api.IndexDefinition;
import org.lilycms.indexer.model.api.IndexState;
import org.lilycms.indexer.model.api.WriteableIndexerModel;
import org.lilycms.indexer.model.impl.IndexerModelImpl;
import org.lilycms.util.zookeeper.ZooKeeperItf;

import java.util.ArrayList;
import java.util.List;

public class AddIndexCli extends BaseIndexerAdminCli {

    public static void main(String[] args) {
        start(args, new AddIndexCli());
    }

    @Override
    public List<Option> getOptions() {
        nameOption.setRequired(true);
        solrShardsOption.setRequired(true);
        configurationOption.setRequired(true);

        List<Option> options = new ArrayList<Option>();
        options.add(nameOption);
        options.add(solrShardsOption);
        options.add(configurationOption);

        return options;
    }

    public void run(ZooKeeperItf zk, CommandLine cmd) throws Exception {
        WriteableIndexerModel model = new IndexerModelImpl(zk);

        IndexDefinition index = model.newIndex(indexName);
        index.setState(IndexState.READY);
        index.setSolrShards(solrShards);
        index.setConfiguration(indexerConfiguration);
        model.addIndex(index);

        System.out.println("Index created: " + indexName);
    }

}
