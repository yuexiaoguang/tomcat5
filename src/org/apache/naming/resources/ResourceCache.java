package org.apache.naming.resources;

import java.util.HashMap;
import java.util.Random;

/**
 * 实现了特殊的缓存.
 */
public class ResourceCache {
    
    
    // ----------------------------------------------------------- Constructors
    
    
    public ResourceCache() {
    }
    
    
    // ----------------------------------------------------- Instance Variables


    /**
     * 用于确定树的元素的随机生成器.
     */
    protected Random random = new Random();


    /**
     * Cache.
     * Path -> Cache entry.
     */
    protected CacheEntry[] cache = new CacheEntry[0];


    /**
     * 未找到的缓存.
     */
    protected HashMap notFoundCache = new HashMap();


    /**
     * 缓存的资源内容最大大小.
     */
    protected int cacheMaxSize = 10240; // 10 MB


    /**
     * 清除的最大大小.
     */
    protected int maxAllocateIterations = 20;


    /**
     * 从缓存中永远不会删除条目的条目命中率.
     * 比较entry.access / hitsCount
     */
    protected long desiredEntryAccessRatio = 3;


    /**
     * 未发现项的备用数量.
     */
    protected int spareNotFoundEntries = 500;


    /**
     * 当前缓存大小, KB.
     */
    protected int cacheSize = 0;


    /**
     * 访问缓存的数量.
     */
    protected long accessCount = 0;


    /**
     * 缓存命中次数.
     */
    protected long hitsCount = 0;


    // ------------------------------------------------------------- Properties


    /**
     * 返回访问计数.
     * Note: 更新不同步, 所以这个数字可能不是完全准确的.
     */
    public long getAccessCount() {
        return accessCount;
    }


    /**
     * 返回缓存的最大大小, KB.
     */
    public int getCacheMaxSize() {
        return cacheMaxSize;
    }


    /**
     * 设置缓存的最大大小, KB.
     */
    public void setCacheMaxSize(int cacheMaxSize) {
        this.cacheMaxSize = cacheMaxSize;
    }


    /**
     * 返回当前缓存大小, KB.
     */
    public int getCacheSize() {
        return cacheSize;
    }


    /**
     * 返回所需的入口访问率.
     */
    public long getDesiredEntryAccessRatio() {
        return desiredEntryAccessRatio;
    }


    /**
     * 设置所需的入口访问率.
     */
    public void setDesiredEntryAccessRatio(long desiredEntryAccessRatio) {
        this.desiredEntryAccessRatio = desiredEntryAccessRatio;
    }


    /**
     * 返回缓存命中次数.
     * Note: 更新不同步, 所以这个数字可能不是完全准确的.
     */
    public long getHitsCount() {
        return hitsCount;
    }


    /**
     * 在空间分配期间返回最大迭代次数.
     */
    public int getMaxAllocateIterations() {
        return maxAllocateIterations;
    }


    /**
     * 设置在空间分配期间最大迭代次数.
     */
    public void setMaxAllocateIterations(int maxAllocateIterations) {
        this.maxAllocateIterations = maxAllocateIterations;
    }


    /**
     * 返回未找到条目的备用数量.
     */
    public int getSpareNotFoundEntries() {
        return spareNotFoundEntries;
    }


    /**
     * 设置未找到条目的备用数量.
     */
    public void setSpareNotFoundEntries(int spareNotFoundEntries) {
        this.spareNotFoundEntries = spareNotFoundEntries;
    }


    // --------------------------------------------------------- Public Methods


    public boolean allocate(int space) {

        int toFree = space - (cacheMaxSize - cacheSize);

        if (toFree <= 0) {
            return true;
        }

        // 增加自由量，这样分配就不必再运行了
        toFree += (cacheMaxSize / 20);

        int size = notFoundCache.size();
        if (size > spareNotFoundEntries) {
            notFoundCache.clear();
            cacheSize -= size;
            toFree -= size;
        }

        if (toFree <= 0) {
            return true;
        }

        int attempts = 0;
        int entriesFound = 0;
        long totalSpace = 0;
        int[] toRemove = new int[maxAllocateIterations];
        while (toFree > 0) {
            if (attempts == maxAllocateIterations) {
                // 放弃，对当前缓存不作任何更改
                return false;
            }
            if (toFree > 0) {
                // 随机选择数组中的条目
                int entryPos = -1;
                boolean unique = false;
                while (!unique) {
                    unique = true;
                    entryPos = random.nextInt(cache.length) ;
                    // Guarantee uniqueness
                    for (int i = 0; i < entriesFound; i++) {
                        if (toRemove[i] == entryPos) {
                            unique = false;
                        }
                    }
                }
                long entryAccessRatio = 
                    ((cache[entryPos].accessCount * 100) / accessCount);
                if (entryAccessRatio < desiredEntryAccessRatio) {
                    toRemove[entriesFound] = entryPos;
                    totalSpace += cache[entryPos].size;
                    toFree -= cache[entryPos].size;
                    entriesFound++;
                }
            }
            attempts++;
        }

        // 现在删除指定的条目
        java.util.Arrays.sort(toRemove, 0, entriesFound);
        CacheEntry[] newCache = new CacheEntry[cache.length - entriesFound];
        int pos = 0;
        int n = -1;
        if (entriesFound > 0) {
            n = toRemove[0];
            for (int i = 0; i < cache.length; i++) {
                if (i == n) {
                    if ((pos + 1) < entriesFound) {
                        n = toRemove[pos + 1];
                        pos++;
                    } else {
                        pos++;
                        n = -1;
                    }
                } else {
                    newCache[i - pos] = cache[i];
                }
            }
        }
        cache = newCache;
        cacheSize -= totalSpace;
        return true;
    }


    public CacheEntry lookup(String name) {

        CacheEntry cacheEntry = null;
        accessCount++;
        int pos = find(cache, name);
        if ((pos != -1) && (name.equals(cache[pos].name))) {
            cacheEntry = cache[pos];
        }
        if (cacheEntry == null) {
            try {
                cacheEntry = (CacheEntry) notFoundCache.get(name);
            } catch (Exception e) {
                // Ignore: 这种查找的可靠性并不重要
            }
        }
        if (cacheEntry != null) {
            hitsCount++;
        }
        return cacheEntry;

    }


    public void load(CacheEntry entry) {
        if (entry.exists) {
            if (insertCache(entry)) {
                cacheSize += entry.size;
            }
        } else {
            int sizeIncrement = (notFoundCache.get(entry.name) == null) ? 1 : 0;
            notFoundCache.put(entry.name, entry);
            cacheSize += sizeIncrement;
        }
    }


    public boolean unload(String name) {
        CacheEntry removedEntry = removeCache(name);
        if (removedEntry != null) {
            cacheSize -= removedEntry.size;
            return true;
        } else if (notFoundCache.remove(name) != null) {
            cacheSize--;
            return true;
        }
        return false;
    }


    /**
     * 在一个map元素排序数组中查找指定名称的 map元素.
     * 这将返回给定数组中最接近的或相等项的索引.
     */
    private static final int find(CacheEntry[] map, String name) {

        int a = 0;
        int b = map.length - 1;

        // Special cases: -1 and 0
        if (b == -1) {
            return -1;
        }
        if (name.compareTo(map[0].name) < 0) {
            return -1;
        }
        if (b == 0) {
            return 0;
        }

        int i = 0;
        while (true) {
            i = (b + a) / 2;
            int result = name.compareTo(map[i].name);
            if (result > 0) {
                a = i;
            } else if (result == 0) {
                return i;
            } else {
                b = i;
            }
            if ((b - a) == 1) {
                int result2 = name.compareTo(map[b].name);
                if (result2 < 0) {
                    return a;
                } else {
                    return b;
                }
            }
        }
    }


    /**
     * 插入到已经排序的MapElement 数组的正确的地方, 防止重复.
     */
    private final boolean insertCache(CacheEntry newElement) {
        CacheEntry[] oldCache = cache;
        int pos = find(oldCache, newElement.name);
        if ((pos != -1) && (newElement.name.equals(oldCache[pos].name))) {
            return false;
        }
        CacheEntry[] newCache = new CacheEntry[cache.length + 1];
        System.arraycopy(oldCache, 0, newCache, 0, pos + 1);
        newCache[pos + 1] = newElement;
        System.arraycopy
            (oldCache, pos + 1, newCache, pos + 2, oldCache.length - pos - 1);
        cache = newCache;
        return true;
    }


    /**
     * 插入到已经排序的MapElement 数组的正确的地方.
     */
    private final CacheEntry removeCache(String name) {
        CacheEntry[] oldCache = cache;
        int pos = find(oldCache, name);
        if ((pos != -1) && (name.equals(oldCache[pos].name))) {
            CacheEntry[] newCache = new CacheEntry[cache.length - 1];
            System.arraycopy(oldCache, 0, newCache, 0, pos);
            System.arraycopy(oldCache, pos + 1, newCache, pos, 
                             oldCache.length - pos - 1);
            cache = newCache;
            return oldCache[pos];
        }
        return null;
    }

}
