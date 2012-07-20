package org.lilyproject.indexer.engine;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;


/**
 * Keeps track of all indexers that have been created. There is one indexer for every index, identified by name.
 *
 * @author Jan Van Besien
 */
public class IndexerRegistry {
    private final Map<String, Indexer> indexers = new HashMap<String, Indexer>();

    public void register(Indexer indexer) {
        indexers.put(indexer.getIndexName(), indexer);
    }

    public void unregister(String indexName) {
        indexers.remove(indexName);
    }

    /**
     * Get the index with the corresponding name.
     *
     * @param indexName name of the index
     * @return index or <code>null</code> if no index with the given name exists
     */
    public Indexer getIndexer(String indexName) {
        return indexers.get(indexName);
    }

    public Collection<Indexer> getAllIndexers() {
        return indexers.values();
    }

}