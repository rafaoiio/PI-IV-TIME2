import com.mongodb.MongoClientSettings;
import com.mongodb.MongoNamespace;
import com.mongodb.client.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.*;
import java.lang.reflect.*;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

// Test double only: no MongoClient, network connection or database writes.
public class MemoryMongo {
    final Map<String,List<Document>> data=new HashMap<>();
    public List<Document> rows(String name) { return data.computeIfAbsent(name,k -> new ArrayList<>()); }
    public void clear() { data.values().forEach(List::clear); }
    static Document bson(Bson b) { return Document.parse(b.toBsonDocument(Document.class,MongoClientSettings.getDefaultCodecRegistry()).toJson()); }
    static Object field(Document row,String path) {
        Object value=row;
        for(String key:path.split("\\.")) value=value instanceof Document d ? d.get(key) : null;
        return value;
    }
    static BigDecimal number(Object n) { return n instanceof Decimal128 d ? d.bigDecimalValue() : new BigDecimal(n.toString()); }
    static boolean numeric(Object n) { return n instanceof Number || n instanceof Decimal128; }
    static Object add(Object a,Object b) {
        if(!numeric(b)) return a;
        if(a instanceof Decimal128 || b instanceof Decimal128) return new Decimal128(number(a).add(number(b),MathContext.DECIMAL128));
        if(a instanceof Double || b instanceof Double) return number(a).doubleValue()+number(b).doubleValue();
        long sum=((Number)a).longValue()+((Number)b).longValue();
        if(sum<=Integer.MAX_VALUE && sum>=Integer.MIN_VALUE) return (int)sum;
        return sum;
    }
    @SuppressWarnings("unchecked") static int compare(Object a,Object b) {
        if(a==null || b==null) return a==b?0:a==null?-1:1;
        if(numeric(a)&&numeric(b)) return number(a).compareTo(number(b));
        return ((Comparable<Object>)a).compareTo(b);
    }
    static boolean matches(Document row,Document query) {
        for(var e:query.entrySet()) {
            if(e.getKey().equals("$and")) {
                for(Document clause:query.getList("$and",Document.class)) if(!matches(row,clause)) return false;
                continue;
            }
            Object value=field(row,e.getKey());
            if(e.getValue() instanceof Document operators) {
                for(var op:operators.entrySet()) {
                    boolean ok=switch(op.getKey()) {
                        case "$in" -> ((List<?>)op.getValue()).contains(value);
                        case "$eq" -> Objects.equals(value,op.getValue());
                        case "$ne" -> !Objects.equals(value,op.getValue());
                        case "$gte" -> value!=null && compare(value,op.getValue())>=0;
                        case "$gt" -> value!=null && compare(value,op.getValue())>0;
                        case "$lt" -> value!=null && compare(value,op.getValue())<0;
                        case "$type" -> "decimal".equals(op.getValue()) && value instanceof Decimal128;
                        default -> throw new AssertionError("Unsupported query "+op);
                    };
                    if(!ok) return false;
                }
            } else if(e.getValue() instanceof org.bson.BsonRegularExpression regex) {
                if(!(value instanceof String text) || !java.util.regex.Pattern.compile(regex.getPattern(),
                        regex.getOptions().contains("i") ? java.util.regex.Pattern.CASE_INSENSITIVE : 0).matcher(text).find()) return false;
            } else if(!Objects.equals(value,e.getValue())) return false;
        }
        return true;
    }
    static Object expression(Object expr,Document row) {
        if(expr instanceof String s) return s.startsWith("$")?field(row,s.substring(1)):s;
        if(!(expr instanceof Document d)) return expr;
        if(d.containsKey("$toString")) return Objects.toString(expression(d.get("$toString"),row),null);
        if(d.containsKey("$dateToString")) {
            Document spec=d.get("$dateToString",Document.class);
            Date date=(Date)expression(spec.get("date"),row);
            String format=spec.getString("format").equals("%Y-%m")?"uuuu-MM":"uuuu-MM-dd";
            return DateTimeFormatter.ofPattern(format).format(date.toInstant().atZone(ZoneId.of(spec.getString("timezone"))));
        }
        String op=d.keySet().iterator().next();
        if(op.equals("$multiply")||op.equals("$divide")) {
            List<?> parts=(List<?>)d.get(op); Object a=expression(parts.get(0),row), b=expression(parts.get(1),row);
            if(a==null||b==null) return null;
            BigDecimal result=op.equals("$multiply")?number(a).multiply(number(b),MathContext.DECIMAL128):number(a).divide(number(b),MathContext.DECIMAL128);
            return new Decimal128(result);
        }
        throw new AssertionError("Unsupported expression "+d);
    }
    static void sort(List<Document> rows,Document spec) {
        rows.sort((a,b) -> {
            for(var key:spec.entrySet()) { int order=compare(field(a,key.getKey()),field(b,key.getKey())); if(order!=0) return order*((Number)key.getValue()).intValue(); }
            return 0;
        });
    }
    List<Document> aggregate(String name,List<Document> pipeline) {
        List<Document> result=new ArrayList<>(rows(name));
        for(Document stage:pipeline) {
            if(stage.containsKey("$match")) { Document filter=stage.get("$match",Document.class); result.removeIf(row -> !matches(row,filter)); }
            else if(stage.containsKey("$group")) {
                Document group=stage.get("$group",Document.class); Map<Object,Document> groups=new LinkedHashMap<>();
                for(Document row:result) {
                    Object id=expression(group.get("_id"),row); Document output=groups.computeIfAbsent(id,k -> new Document("_id",k));
                    for(var e:group.entrySet()) if(!e.getKey().equals("_id")) {
                        Document accumulator=(Document)e.getValue();
                        if(accumulator.containsKey("$sum")) output.put(e.getKey(),add(output.getOrDefault(e.getKey(),0),expression(accumulator.get("$sum"),row)));
                        else if(accumulator.containsKey("$first")&&!output.containsKey(e.getKey())) output.put(e.getKey(),expression(accumulator.get("$first"),row));
                    }
                }
                result=new ArrayList<>(groups.values());
            } else if(stage.containsKey("$lookup")) {
                Document spec=stage.get("$lookup",Document.class); List<Document> joined=new ArrayList<>();
                for(Document row:result) joined.add(new Document(row).append(spec.getString("as"),rows(spec.getString("from")).stream()
                    .filter(other -> Objects.equals(field(row,spec.getString("localField")),field(other,spec.getString("foreignField")))).toList()));
                result=joined;
            } else if(stage.containsKey("$unwind")) {
                String key=stage.getString("$unwind").substring(1); List<Document> expanded=new ArrayList<>();
                for(Document row:result) for(Document value:row.getList(key,Document.class)) expanded.add(new Document(row).append(key,value));
                result=expanded;
            } else if(stage.containsKey("$project")) {
                Document projection=stage.get("$project",Document.class); List<Document> projected=new ArrayList<>();
                for(Document row:result) {
                    Document output=new Document();
                    for(var e:projection.entrySet()) {
                        if(Integer.valueOf(0).equals(e.getValue())) continue;
                        output.put(e.getKey(),Integer.valueOf(1).equals(e.getValue())?row.get(e.getKey()):expression(e.getValue(),row));
                    }
                    projected.add(output);
                }
                result=projected;
            } else if(stage.containsKey("$count")) {
                result=result.isEmpty()?new ArrayList<>():new ArrayList<>(List.of(new Document(stage.getString("$count"),result.size())));
            } else if(stage.containsKey("$sort")) sort(result,stage.get("$sort",Document.class));
            else throw new AssertionError("Unsupported stage "+stage);
        }
        return result;
    }
    @SuppressWarnings("unchecked") public MongoCollection<Document> collection(String name) {
        return (MongoCollection<Document>)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{MongoCollection.class},(p,m,a) -> {
            return switch(m.getName()) {
                case "getNamespace" -> new MongoNamespace("qa_isolated",name);
                case "countDocuments" -> rows(name).stream().filter(row -> a==null||a.length==0||matches(row,bson((Bson)a[0]))).count();
                case "find" -> iterable(rows(name).stream().filter(row -> a==null||a.length==0||matches(row,bson((Bson)a[0]))).toList(),FindIterable.class);
                case "aggregate" -> iterable(aggregate(name,((List<Bson>)a[0]).stream().map(MemoryMongo::bson).toList()),AggregateIterable.class);
                default -> throw new AssertionError("Forbidden collection operation "+m.getName());
            };
        });
    }
    @SuppressWarnings("unchecked") Object iterable(List<Document> input,Class<?> type) {
        List<Document> list=new ArrayList<>(input);
        return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{type},(p,m,a) -> {
            return switch(m.getName()) {
                case "first" -> list.isEmpty()?null:list.get(0);
                case "into" -> { ((Collection<Document>)a[0]).addAll(list); yield a[0]; }
                case "sort" -> { sort(list,bson((Bson)a[0])); yield p; }
                case "iterator" -> {
                    Iterator<Document> iterator=list.iterator();
                    yield Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{MongoCursor.class},(cp,cm,ca) -> switch(cm.getName()) {
                        case "hasNext" -> iterator.hasNext(); case "next" -> iterator.next(); case "close" -> null;
                        default -> throw new AssertionError(cm.getName());
                    });
                }
                default -> throw new AssertionError("Unexpected iterable operation "+m.getName());
            };
        });
    }
}
