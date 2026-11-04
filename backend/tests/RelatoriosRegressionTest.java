import controller.*;
import com.sun.net.httpserver.*;
import org.bson.Document;
import org.bson.types.*;
import java.net.*;
import java.net.http.*;
import java.io.*;
import java.math.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public class RelatoriosRegressionTest {
    static final MemoryMongo db=new MemoryMongo();
    static final ZoneId BR=ZoneId.of("America/Sao_Paulo");
    static final LocalDate TODAY=LocalDate.now(BR);
    static final String DASH="/api/dashboard/resumo", NEXT="/api/lotes/proximos-vencimento?dias=7";
    static final String BASE="/api/relatorios/", IND=BASE+"indicadores", STOCK=BASE+"estoque-por-produto";
    static final String PROD=BASE+"desperdicios/por-produto", REASON=BASE+"desperdicios/por-motivo";
    static final String PERIOD=BASE+"desperdicios/por-periodo", MONTH=BASE+"desperdicios/evolucao-mensal", FIN=BASE+"financeiro-por-produto";
    static final String RANGE="?inicio=2026-10-01&fim=2026-10-31";
    static final String[] ROUTES={DASH,NEXT,IND,STOCK,PROD,REASON,PERIOD+RANGE,MONTH,FIN};
    static final List<Document> results=new ArrayList<>();
    static HttpClient client; static String base; static String lastException="";
    interface Test { void run() throws Exception; }
    static void test(String name,Test test) {
        long begin=System.nanoTime(); lastException="";
        try { test.run(); results.add(new Document("name",name).append("passed",true)); System.out.println("PASS "+name); }
        catch(Throwable error) {
            String detail=error+(!lastException.isEmpty()?"; handler="+lastException:"");
            results.add(new Document("name",name).append("passed",false).append("detail",detail)); System.out.println("FAIL "+name+" :: "+detail);
        }
        results.get(results.size()-1).append("ms",(System.nanoTime()-begin)/1000000);
    }
    static void require(boolean ok,String detail) { if(!ok) throw new AssertionError(detail); }
    static HttpResponse<String> request(String route,String method) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base+route)).timeout(Duration.ofSeconds(4))
            .header("Origin","http://localhost:5500").method(method,HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
    }
    static Document get(String route) throws Exception {
        var response=request(route,"GET"); require(response.statusCode()==200,"HTTP "+response.statusCode()+" "+response.body());
        require(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"),"JSON content type");
        return Document.parse(response.body());
    }
    static void status(String route,String method,int expected) throws Exception {
        var response=request(route,method); require(response.statusCode()==expected,"expected HTTP "+expected+", actual "+response.statusCode()+" "+response.body());
    }
    static Decimal128 dec(String n) { return new Decimal128(new BigDecimal(n)); }
    static BigDecimal number(Object n) { return MemoryMongo.number(n); }
    static long count(Object n) { return ((Number)n).longValue(); }
    static List<Document> list(Document d,String key) { return d.getList(key,Document.class); }
    static BigDecimal total(Document response,String key,String amount) {
        return list(response,key).stream().map(d -> number(d.get(amount))).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    static ObjectId product(String name,String unit,String status) {
        ObjectId id=new ObjectId(); db.rows("itens").add(new Document("_id",id).append("nome",name).append("unidadeMedida",unit).append("status",status)); return id;
    }
    static ObjectId lot(ObjectId item,int days,String quantity,String initial,String cost) {
        ObjectId id=new ObjectId(); db.rows("lotes").add(new Document("_id",id).append("codigo",days+"-"+quantity).append("itemId",item)
            .append("quantidadeDisponivel",dec(quantity)).append("quantidadeInicial",dec(initial)).append("custoTotalAquisicao",dec(cost))
            .append("dataValidade",Date.from(TODAY.plusDays(days).atStartOfDay(ZoneOffset.UTC).toInstant()))); return id;
    }
    static void move(ObjectId lot,String type,String quantity,String reason,Instant instant) {
        db.rows("movimentacoes").add(new Document("loteId",lot).append("tipo",type).append("quantidade",dec(quantity))
            .append("motivo",reason).append("dataMovimentacao",Date.from(instant)));
    }
    static ObjectId setupDay(int day) { db.clear(); ObjectId id=product("Produto","kg","ativo"); return lot(id,day,"20","100","500"); }
    static void finance() {
        db.clear(); ObjectId rice=product("Arroz","kg","ativo"),milk=product("Leite","L","ativo"),consumed=product("Consumido","un","ativo");
        ObjectId a=lot(rice,1,"5","10","100"),b=lot(rice,2,"9","10","200"),c=lot(milk,1,"1.7","2","0.60"),d=lot(consumed,1,"8","10","1000");
        Instant date=Instant.parse("2026-10-29T03:00:00Z");
        move(a,"VENCIMENTO","2","Vencimento",date); move(a,"AJUSTE_SAIDA","3","Danificado",date); move(b,"AJUSTE_SAIDA","1","Outro",date);
        move(c,"VENCIMENTO","0.1","Vencimento",date); move(c,"AJUSTE_SAIDA","0.2","Danificado",date); move(d,"CONSUMO","2","Consumido",date);
    }
    static Document rice(Document response) { return list(response,"financeiroPorProduto").stream().filter(d -> d.getString("nome").equals("Arroz")).findFirst().orElseThrow(); }
    static void amount(Document d,String field,String expected) { require(number(d.get(field)).compareTo(new BigDecimal(expected))==0,field+" expected="+expected+" actual="+d.get(field)); }
    static HttpHandler wrap(HttpHandler handler) {
        return exchange -> { try { handler.handle(exchange); } catch(Throwable error) { lastException=error.toString(); exchange.close(); } };
    }
    public static void main(String[] args) throws Exception {
        var executor=Executors.newCachedThreadPool(); var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/dashboard/resumo",wrap(new DashboardHandler(db.collection("itens"),db.collection("lotes"))));
        server.createContext("/api/lotes/proximos-vencimento",wrap(new ProximosVencimentoHandler(db.collection("lotes"),db.collection("itens"))));
        server.createContext("/api/relatorios",wrap(new RelatoriosHandler(db.collection("itens"),db.collection("lotes"),db.collection("movimentacoes"))));
        server.setExecutor(executor); server.start(); base="http://127.0.0.1:"+server.getAddress().getPort();
        client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        try {
            for(String route:ROUTES) test("EMPTY GET "+route,() -> {
                db.clear(); Document body=get(route);
                if(route.equals(DASH)) require(count(body.get("totalLotes"))==0 && list(body,"saldoPorProduto").isEmpty(),body.toJson());
                else if(route.equals(IND)) require(count(body.get("desperdicados"))==0 && body.get("motivoMaisFrequente")==null && number(body.get("perdasFinanceiras")).signum()==0,body.toJson());
                else if(route.equals(NEXT)) require(count(body.get("total"))==0 && list(body,"lotes").isEmpty(),body.toJson());
                else if(route.equals(MONTH)) require(list(body,"evolucaoMensal").size()==6 && list(body,"evolucaoMensal").stream().allMatch(d -> count(d.get("totalOcorrencias"))==0),body.toJson());
                else require(((List<?>)body.values().iterator().next()).isEmpty(),body.toJson());
            });
            for(String route:ROUTES) test("METHOD POST "+route,() -> status(route,"POST",405));
            for(String route:ROUTES) test("METHOD OPTIONS "+route,() -> status(route,"OPTIONS",route.equals(NEXT)?405:204));
            for(String route:List.of("/api/nao-existe",DASH+"/errado",BASE+"nao-existe","/api/lotes/proximos-vencimento/errado"))
                test("UNKNOWN "+route,() -> status(route,"GET",404));
            String[] invalidPeriods={"","?inicio=2026-10-01","?fim=2026-10-31","?inicio=&fim=2026-10-31",
                "?inicio=2026-13-01&fim=2026-10-31","?inicio=2026-02-30&fim=2026-10-31","?inicio=2026-11-01&fim=2026-10-31",
                "?inicio=2026-10-01&fim=2026-10-31&inicio=2026-10-02","?inicio=2026-10-01&fim=2026-10-31&extra=1",
                "?inicio=2026-10-01&fim=+999999999-12-31","?inicio=2026-10-01&fim=+999999999-12-30","?inicio=-999999999-01-01&fim=2026-10-31"};
            for(String query:invalidPeriods) test("INVALID PERIOD "+query,() -> status(PERIOD+query,"GET",400));
            for(String value:List.of("0","-1","2","abc","","9999999999999")) test("INVALID DAYS "+value,() -> status("/api/lotes/proximos-vencimento?dias="+value,"GET",400));
            test("DAYS default",() -> { db.clear(); get("/api/lotes/proximos-vencimento"); });
            for(int day:new int[]{-1,0,1}) {
                test("VALIDITY DASH day="+day,() -> { setupDay(day); amount(new Document("sum",total(get(DASH),"saldoPorProduto","quantidadeTotal")),"sum",day<0?"0":"20"); });
                test("VALIDITY REPORT day="+day,() -> { setupDay(day); amount(new Document("sum",total(get(STOCK),"estoquePorProduto","quantidadeTotal")),"sum",day<0?"0":"20"); });
                test("VALIDITY UPCOMING day="+day,() -> { setupDay(day); Document body=get(NEXT); require(count(body.get("total"))==(day<0?0:1),body.toJson()); });
            }
            test("UPCOMING excludes day +7",() -> { setupDay(7); require(count(get(NEXT).get("total"))==0,"Included first day outside seven-day window"); });
            test("UPCOMING preserves stored calendar date",() -> { setupDay(1); String actual=list(get(NEXT),"lotes").get(0).getString("dataValidade"); require(TODAY.plusDays(1).toString().equals(actual),"Expected "+TODAY.plusDays(1)+", actual "+actual); });
            for(String route:List.of(DASH,STOCK,NEXT)) test("NONPOSITIVE STOCK "+route,() -> {
                db.clear(); ObjectId p=product("Produto","kg","ativo"); lot(p,1,"0","10","50"); lot(p,1,"-2","10","50");
                Document body=get(route); require(((List<?>)body.get(route.equals(DASH)?"saldoPorProduto":route.equals(STOCK)?"estoquePorProduto":"lotes")).isEmpty(),body.toJson());
            });
            test("DASH and UPCOMING share active/archive rules",() -> {
                db.clear(); ObjectId active=product("Ativo","kg","ativo"),inactive=product("Inativo","kg","inativo");
                lot(active,1,"1","1","5"); lot(inactive,1,"1","1","5"); lot(active,1,"1","1","5"); db.rows("lotes").get(2).put("arquivado",true);
                long dash=count(get(DASH).get("lotesProximosVencimento")),next=count(get(NEXT).get("total")); require(dash==next,"Dashboard="+dash+", upcoming list="+next);
            });
            test("DASH and STOCK share product membership",() -> {
                db.clear(); ObjectId p=product("Existe","kg","ativo"); lot(p,1,"25","25","125"); lot(new ObjectId(),1,"50","50","250");
                BigDecimal dash=total(get(DASH),"saldoPorProduto","quantidadeTotal"),report=total(get(STOCK),"estoquePorProduto","quantidadeTotal");
                require(dash.compareTo(report)==0,"Dashboard="+dash+", reports="+report);
            });
            test("OCCURRENCES counts movements not quantity",() -> { finance(); require(count(get(IND).get("desperdicados"))==5,"Expected five waste events"); });
            test("QUANTITY groups waste and excludes consumption",() -> { finance(); var body=get(PROD); require(list(body,"desperdiciosPorProduto").size()==2,body.toJson()); amount(new Document("sum",total(body,"desperdiciosPorProduto","quantidadeTotal")),"sum","6.3"); });
            test("REASONS agree with occurrence indicator",() -> { finance(); require(total(get(REASON),"desperdiciosPorMotivo","totalOcorrencias").compareTo(BigDecimal.valueOf(count(get(IND).get("desperdicados"))))==0,"Reason total mismatch"); });
            test("FINANCE different costs and repeated lot",() -> { finance(); Document row=rice(get(FIN)); amount(row,"quantidadeDesperdicada","6"); amount(row,"valorPerdido","70"); amount(row,"valorTotalEstoque","300"); amount(row,"valorUnitario","11.66666666666666666666666666666667"); });
            test("FINANCE equal-cost distinct lots counted separately",() -> { finance(); db.rows("lotes").get(1).put("custoTotalAquisicao",dec("100")); amount(rice(get(FIN)),"valorTotalEstoque","200"); });
            test("FINANCE fractional Decimal128 values",() -> { finance(); Document row=list(get(FIN),"financeiroPorProduto").stream().filter(d -> d.getString("nome").equals("Leite")).findFirst().orElseThrow(); amount(row,"quantidadeDesperdicada","0.3"); amount(row,"valorPerdido","0.09"); });
            test("FINANCE indicator agrees with product report Decimal128",() -> { finance(); BigDecimal indicator=number(get(IND).get("perdasFinanceiras")),report=total(get(FIN),"financeiroPorProduto","valorPerdido"); require(indicator.compareTo(report)==0 && report.compareTo(new BigDecimal("70.09"))==0,"Indicator="+indicator+", report="+report); });
            test("FINANCE indicator agrees for numeric acquisition cost",() -> { finance(); db.rows("lotes").get(0).put("custoTotalAquisicao",100); BigDecimal indicator=number(get(IND).get("perdasFinanceiras")),report=total(get(FIN),"financeiroPorProduto","valorPerdido"); require(indicator.compareTo(report)==0,"Indicator="+indicator+", report="+report); });
            test("FINANCE zero divisor returns unavailable derived values",() -> { finance(); db.rows("lotes").get(0).put("quantidadeInicial",dec("0")); Document row=rice(get(FIN)); require(row.get("valorUnitario")==null && row.get("valorPerdido")==null,row.toJson()); amount(row,"valorTotalEstoque","300"); get(IND); });
            test("FINANCE consumption-only data has no waste",() -> { finance(); db.rows("movimentacoes").removeIf(d -> !d.getString("tipo").equals("CONSUMO")); require(list(get(FIN),"financeiroPorProduto").isEmpty(),"Consumption in finance"); require(count(get(IND).get("desperdicados"))==0,"Consumption in indicator"); });
            test("PERIOD Sao Paulo boundaries inclusive final day",() -> {
                setupDay(1); ObjectId lot=db.rows("lotes").get(0).getObjectId("_id");
                for(String date:List.of("2026-10-01T02:59:59Z","2026-10-01T03:00:00Z","2026-11-01T02:59:59Z","2026-11-01T03:00:00Z")) move(lot,"VENCIMENTO","2","Vencimento",Instant.parse(date));
                Document response=get(PERIOD+RANGE); List<Document> days=list(response,"desperdicioPorPeriodo");
                require(days.size()==2 && days.get(0).getString("data").equals("2026-10-01") && days.get(1).getString("data").equals("2026-10-31"),response.toJson());
                require(total(response,"desperdicioPorPeriodo","totalOcorrencias").intValueExact()==2,response.toJson());
            });
            test("PERIOD same start/end day",() -> { finance(); require(total(get(PERIOD+"?inicio=2026-10-29&fim=2026-10-29"),"desperdicioPorPeriodo","totalOcorrencias").intValueExact()==5,"Same-day events lost"); });
            test("MONTHLY six ordered months with gaps and boundaries",() -> {
                setupDay(1); ObjectId lot=db.rows("lotes").get(0).getObjectId("_id"); YearMonth current=YearMonth.now(BR),first=current.minusMonths(5);
                Instant lower=first.atDay(1).atStartOfDay(BR).toInstant(),upper=current.plusMonths(1).atDay(1).atStartOfDay(BR).toInstant();
                for(Instant time:List.of(lower.minusMillis(1),lower,upper.minusMillis(1),upper)) move(lot,"VENCIMENTO","10","Vencimento",time);
                move(lot,"CONSUMO","20","Consumido",lower);
                List<Document> months=list(get(MONTH),"evolucaoMensal"); require(months.size()==6,"Expected six months");
                for(int i=0;i<6;i++) require(months.get(i).getString("mes").equals(first.plusMonths(i).toString()) && count(months.get(i).get("totalOcorrencias"))==(i==0||i==5?1:0),months.toString());
            });
            test("FIX 4 upcoming uses expected UTC listing criteria",() -> {
                db.clear(); ObjectId active=product("Ativo","kg","ativo"),inactive=product("Inativo","kg","inativo");
                for(int day:new int[]{-1,0,1,6,7}) lot(active,day,"10","10","50");
                lot(inactive,1,"10","10","50"); lot(new ObjectId(),1,"10","10","50");
                lot(active,1,"0","10","50"); lot(active,1,"-1","10","50");
                lot(active,1,"10","10","50"); db.rows("lotes").get(9).put("arquivado",true);
                require(count(get(DASH).get("lotesProximosVencimento"))==3,"Expected today, tomorrow and day +6 only");
            });
            test("FIX 4 active status is case insensitive like listing",() -> {
                db.clear(); ObjectId item=product("Ativo","kg","ATIVO"); lot(item,0,"10","10","50");
                require(count(get(DASH).get("lotesProximosVencimento"))==1,"Uppercase ATIVO must be eligible");
            });
            test("FIX 3 stock keeps existing inactive and archived product rules",() -> {
                db.clear(); ObjectId active=product("Ativo","kg","ativo"),inactive=product("Inativo","kg","inativo");
                lot(active,1,"20","20","100"); lot(inactive,1,"30","30","150"); db.rows("lotes").get(0).put("arquivado",true);
                amount(new Document("sum",total(get(DASH),"saldoPorProduto","quantidadeTotal")),"sum","50");
                amount(new Document("sum",total(get(STOCK),"estoquePorProduto","quantidadeTotal")),"sum","50");
            });
            test("FIX 5 double acquisition retains decimal digits",() -> {
                finance(); db.rows("lotes").get(0).put("custoTotalAquisicao",100.12345678901234d);
                BigDecimal expected=new BigDecimal("70.15172839450617");
                require(number(get(IND).get("perdasFinanceiras")).compareTo(expected)==0,"Double indicator precision");
                require(total(get(FIN),"financeiroPorProduto","valorPerdido").compareTo(expected)==0,"Double report precision");
            });
            test("FIX 5 integer and double quantities and denominators",() -> {
                finance(); db.rows("lotes").get(0).put("quantidadeInicial",10.0d); db.rows("lotes").get(0).put("custoTotalAquisicao",100L);
                db.rows("movimentacoes").get(0).put("quantidade",2); db.rows("movimentacoes").get(1).put("quantidade",3.0d);
                amount(get(IND),"perdasFinanceiras","70.09"); amount(new Document("total",total(get(FIN),"financeiroPorProduto","valorPerdido")),"total","70.09");
            });
            test("FIX 5 no rounded intermediate unit cost",() -> {
                db.clear(); ObjectId item=product("Fracao","kg","ativo"),lot=lot(item,1,"0","3","1");
                move(lot,"VENCIMENTO","1","Vencimento",Instant.parse("2026-10-29T03:00:00Z"));
                move(lot,"AJUSTE_SAIDA","2","Danificado",Instant.parse("2026-10-29T03:00:00Z"));
                amount(get(IND),"perdasFinanceiras","1"); amount(list(get(FIN),"financeiroPorProduto").get(0),"valorPerdido","1");
            });
            test("FIX 5 invalid costs and zero divisor stay safe",() -> {
                finance(); db.rows("lotes").get(0).put("quantidadeInicial",dec("0")); db.rows("lotes").get(1).put("custoTotalAquisicao",Double.NaN);
                amount(get(IND),"perdasFinanceiras","0.09"); require(rice(get(FIN)).get("valorPerdido")==null,"Invalid loss must remain unavailable");
            });
            test("FIX 6 extreme dates return JSON 400 after boundary conversion",() -> {
                for(String query:List.of("?inicio=2026-10-01&fim=+999999999-12-31","?inicio=-999999999-01-01&fim=2026-10-31","?inicio=2026-10-01&fim=+999999999-12-30")) {
                    var response=request(PERIOD+query,"GET"); require(response.statusCode()==400,"Expected 400");
                    require(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"),"JSON error");
                    require(Document.parse(response.body()).getString("mensagem").equals("Intervalo de datas fora do limite permitido."),response.body());
                }
            });
        } finally {
            server.stop(0); executor.shutdownNow(); client.close();
            long passed=results.stream().filter(d -> d.getBoolean("passed")).count();
            Document report=new Document("environment","real HTTP / freshly compiled handlers / mocked Mongo collections")
                .append("javaRuntime",System.getProperty("java.version")).append("total",results.size()).append("passed",passed).append("failed",results.size()-passed).append("tests",results);
            Files.writeString(Path.of(args[0],"isolated-results.json"),report.toJson());
            System.out.println("SUMMARY "+results.size()+" tests, "+passed+" passed, "+(results.size()-passed)+" failed");
        }
        if(results.stream().anyMatch(d -> !d.getBoolean("passed"))) System.exit(1);
    }
}
