package org.lilycms.repository.impl.lock;

import java.io.IOException;

import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;

public class RowLocker {

    private final HTable table;
    private final byte[] family;
    private final byte[] qualifier;
    private final long timeout;
    private org.apache.hadoop.hbase.client.RowLock hbaseRowLock;

    public RowLocker(HTable table, byte[] family, byte[] qualifier, long timeout) {
        this.table = table;
        this.family = family;
        this.qualifier = qualifier;
        this.timeout = timeout;
    }
    
    public RowLock lockRow(byte[] rowKey) throws IOException {
        long now = System.currentTimeMillis();
        Get get = new Get(rowKey);
        get.addColumn(family, qualifier);

        if (!table.exists(get)) {
            hbaseRowLock = table.lockRow(rowKey);
            if (hbaseRowLock == null) return null;
            try {
                Put put = new Put(rowKey, hbaseRowLock);
                byte[] lock = Bytes.toBytes(now);
                put.add(family, qualifier, lock);
                table.put(put);
                return new RowLock(rowKey, now);
            } finally {
                table.unlockRow(hbaseRowLock);
            }
        } else {
            Result result = table.get(get);
            byte[] previousLock = null;
            long previousTimestamp = -1L;
            if (!result.isEmpty()) {
                previousLock = result.getValue(family, qualifier);
                if (previousLock != null) {
                    previousTimestamp = Bytes.toLong(previousLock);
                }
            }
            if ((previousTimestamp == -1) || (previousTimestamp + timeout  < now)) {
                Put put = new Put(rowKey);
                byte[] lock = Bytes.toBytes(now);
                put.add(family, qualifier, lock);
                if (table.checkAndPut(rowKey, family, qualifier, previousLock, put)) {
                    return new RowLock(rowKey, now);
                }
            }
            return null;
        }
    }
    
    public void unlockRow(RowLock lock) throws IOException {
        byte[] rowKey = lock.getRowKey();
        Put put = new Put(rowKey);
        put.add(family, qualifier, Bytes.toBytes(-1L));
        table.checkAndPut(rowKey, family, qualifier, Bytes.toBytes(lock.getTimestamp()), put); // If it fails, we already lost the lock
    }
    
    public boolean isLocked(byte[] rowKey) throws IOException {
        long now = System.currentTimeMillis();
        Get get = new Get(rowKey);
        get.addColumn(family, qualifier);
        Result result = table.get(get);

        if (result.isEmpty()) return false;
        
        byte[] lock = result.getValue(family, qualifier);
        if (lock == null) return false;
        
        long previousTimestamp = Bytes.toLong(lock);
        if (previousTimestamp == -1) return false;
        if (previousTimestamp + timeout < now) return false;
        
        return true;
    }
    
    public boolean put(Put put, RowLock lock) throws IOException {
        if (!Bytes.equals(put.getRow(), lock.getRowKey()))
                return false;
        return table.checkAndPut(lock.getRowKey(), family, qualifier, Bytes.toBytes(lock.getTimestamp()), put);
    }
}