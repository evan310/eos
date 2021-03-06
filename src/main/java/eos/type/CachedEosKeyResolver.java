package eos.type;

import eos.collections.CalculationCache;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CachedEosKeyResolver implements EosKeyResolver, EosKeyCombinator{
    static final Pattern RegexPattern = Pattern.compile("^([a-z0-9\\-_]*)\\+([a-z\\-]*)://(.+)", Pattern.CASE_INSENSITIVE);

    final byte allowedTagsCount;
    final CalculationCache<String, EosKey> resolveCache;
    final CalculationCache<EosKey, EosKey[]> combinationCache;

    public CachedEosKeyResolver(byte allowedTagsCount, int resolveCacheCapacity, int combinationCacheCapacity)
    {
        if (allowedTagsCount < 1) {
            throw new IllegalArgumentException("Allowed tags count must be greater, than zero");
        }
        this.allowedTagsCount = allowedTagsCount;
        resolveCache = new CalculationCache<>(resolveCacheCapacity, new CalculationCache.Supplier<String, EosKey>() {
            @Override
            public EosKey calculate(String in) {
                return CachedEosKeyResolver.parse(in);
            }
        });
        combinationCache = new CalculationCache<>(combinationCacheCapacity, new CalculationCache.Supplier<EosKey, EosKey[]>() {
            @Override
            public EosKey[] calculate(EosKey in) {
                return CachedEosKeyResolver.this.recombination(in);
            }
        });
    }

    @Override
    public EosKey[] getCombinations(EosKey origin) {
        if (origin == null) {
            throw new NullPointerException("Origin key in null");
        }
        return combinationCache.get(origin);
    }

    @Override
    public EosKey resolve(String source) {
        if (source == null) {
            throw new NullPointerException("Source string is null");
        }
        return resolveCache.get(source);
    }

    /**
     * Parses incoming string and produces EosKey
     *
     * @param source String to parse
     * @return Generated from url EosKey
     */
    public static EosKey parse(String source)
    {
        if (source == null) {
            throw new NullPointerException("Source string is null");
        }

        Matcher matcher = RegexPattern.matcher(source);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Unknown format");
        }

        String realm         = matcher.group(1);
        EosKey.Schema schema = EosKey.Schema.valueOf(matcher.group(2));
        String[] tags        = matcher.group(3).split(":");

        return new EosKey(realm, schema, tags);
    }

    /**
     * Returns array of all possible combinations of tags and server
     *
     * @param origin Original key
     * @return List of combinations
     */
    public EosKey[] recombination(EosKey origin)
    {
        // Checking limit
        if (origin.getTags().length > allowedTagsCount) {
            // Limit exceed
            return new EosKey[]{origin};
        }

        List<EosKey> combinations = new ArrayList<>();

        // First is origin by itself
        combinations.add(origin);

        // Adding combinations
        for (String[] tags : recombine(origin.getTags())) {
            EosKey newKey = new EosKey(origin.getRealm(), origin.getSchema(), tags);
            combinations.add(newKey);
        }

        return combinations.toArray(new EosKey[combinations.size()]);
    }

    /**
     * Returns list of recombined tags
     *
     * @param tags Tags array
     * @return List of combinations
     */
    static List<String[]> recombine(String[] tags)
    {
        List<String[]> list = new ArrayList<>();
        if (tags.length < 2) {
            // Do nothing
        } else if (tags.length == 2) {
            // Hardcoded
            list.add(new String[]{tags[0]});
            list.add(new String[]{tags[1]});
        } else if (tags.length == 3) {
            // Hardcoded
            list.add(new String[]{tags[0], tags[1]});
            list.add(new String[]{tags[0], tags[2]});
            list.add(new String[]{tags[1], tags[2]});
            list.add(new String[]{tags[0]});
            list.add(new String[]{tags[1]});
            list.add(new String[]{tags[2]});
        } else {
            // Recursive
            for (int i=0; i < tags.length; i++) {
                String[] part = new String[tags.length-1];
                int z = 0;
                for (int j=0; j < tags.length; j++) {
                    if (j != i) part[z++] = tags[j];
                }
                list.add(part);
                list.addAll(recombine(part));
            }
        }

        return list;
    }
}
