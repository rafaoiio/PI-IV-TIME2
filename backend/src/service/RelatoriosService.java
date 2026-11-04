package service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;

import org.bson.Document;
import org.bson.types.Decimal128;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RelatoriosService {

        private final MongoCollection<Document> itens;
        private final MongoCollection<Document> lotes;
        private final MongoCollection<Document> movimentacoes;

        public RelatoriosService(MongoCollection<Document> itens, MongoCollection<Document> lotes,
                        MongoCollection<Document> movimentacoes) {
                this.itens = itens;
                this.lotes = lotes;
                this.movimentacoes = movimentacoes;
        }

        public Document consultarEstoquePorProduto() {
                ZoneId fuso = ZoneId.of("America/Sao_Paulo");
                LocalDate hoje = LocalDate.now(fuso);
                // A data de validade é armazenada à meia-noite UTC.
                Date inicioHoje = Date.from(hoje.atStartOfDay(ZoneOffset.UTC).toInstant());

                // A validade de hoje é aceita até o final do dia no fuso local.
                List<Document> estoquePorProduto = lotes.aggregate(
                                List.of(
                                                Aggregates.match(
                                                                Filters.and(
                                                                                Filters.gte("dataValidade", inicioHoje),
                                                                                Filters.gt("quantidadeDisponivel",
                                                                                                new Decimal128(BigDecimal.ZERO)))),
                                                Aggregates.group(
                                                                "$itemId",
                                                                Accumulators.sum("quantidadeTotal",
                                                                                "$quantidadeDisponivel")),
                                                Aggregates.lookup(
                                                                itens.getNamespace().getCollectionName(), "_id", "_id",
                                                                "produto"),
                                                // Retorna somente grupos associados a um produto cadastrado.
                                                Aggregates.unwind("$produto"),
                                                Aggregates.project(
                                                                Projections.fields(
                                                                                Projections.excludeId(),
                                                                                Projections.computed("itemId",
                                                                                                new Document("$toString",
                                                                                                                "$_id")),
                                                                                Projections.computed("nome",
                                                                                                "$produto.nome"),
                                                                                Projections.computed("unidadeMedida",
                                                                                                "$produto.unidadeMedida"),
                                                                                Projections.include(
                                                                                                "quantidadeTotal")))))
                                .into(new ArrayList<>());

                return new Document("estoquePorProduto", estoquePorProduto);
        }

        public Document consultarFinanceiroPorProduto() {
                // Uma linha por lote impede repetir seu custo original de aquisição.
                List<Document> desperdiciosPorLote = movimentacoes.aggregate(List.of(
                                Aggregates.match(Filters.in("tipo", "VENCIMENTO", "AJUSTE_SAIDA")),
                                Aggregates.group("$loteId",
                                                Accumulators.sum("quantidadeDesperdicada", "$quantidade")),
                                Aggregates.lookup(lotes.getNamespace().getCollectionName(), "_id", "_id", "lote"),
                                Aggregates.unwind("$lote"),
                                Aggregates.lookup(itens.getNamespace().getCollectionName(), "lote.itemId", "_id",
                                                "produto"),
                                Aggregates.unwind("$produto"),
                                Aggregates.project(Projections.fields(
                                                Projections.excludeId(),
                                                Projections.computed("itemId", new Document("$toString", "$produto._id")),
                                                Projections.computed("nome", "$produto.nome"),
                                                Projections.computed("unidadeMedida", "$produto.unidadeMedida"),
                                                Projections.computed("quantidadeInicial", "$lote.quantidadeInicial"),
                                                Projections.computed("custoTotalAquisicao", "$lote.custoTotalAquisicao"),
                                                Projections.include("quantidadeDesperdicada"))),
                                Aggregates.sort(Sorts.ascending("nome", "itemId"))))
                                .into(new ArrayList<>());

                Map<String, List<Document>> lotesPorProduto = new LinkedHashMap<>();
                for (Document lote : desperdiciosPorLote) {
                        lotesPorProduto.computeIfAbsent(lote.getString("itemId"), chave -> new ArrayList<>()).add(lote);
                }

                List<Document> financeiroPorProduto = new ArrayList<>();
                for (List<Document> lotesDoProduto : lotesPorProduto.values()) {
                        BigDecimal quantidade = BigDecimal.ZERO;
                        BigDecimal valorTotalEstoque = BigDecimal.ZERO;
                        BigDecimal valorPerdido = BigDecimal.ZERO;

                        for (Document lote : lotesDoProduto) {
                                BigDecimal desperdicio = decimalFinanceiro(lote.get("quantidadeDesperdicada"));
                                BigDecimal inicial = decimalFinanceiro(lote.get("quantidadeInicial"));
                                BigDecimal custo = decimalFinanceiro(lote.get("custoTotalAquisicao"));
                                if (desperdicio == null) {
                                        throw new IllegalArgumentException("Quantidade de desperdício inválida no lote.");
                                }
                                quantidade = quantidade.add(desperdicio);

                                boolean custoValido = custo != null && custo.signum() >= 0;
                                valorTotalEstoque = custoValido && valorTotalEstoque != null
                                                ? valorTotalEstoque.add(custo) : null;

                                // Dados incompletos não devem resultar em uma perda financeira fictícia de zero.
                                if (!custoValido || inicial == null || inicial.signum() <= 0) {
                                        valorPerdido = null;
                                } else if (valorPerdido != null) {
                                        valorPerdido = valorPerdido.add(calcularPerdaLote(desperdicio, inicial, custo));
                                }
                        }

                        BigDecimal valorUnitario = valorPerdido != null && quantidade.signum() > 0
                                        ? valorPerdido.divide(quantidade, MathContext.DECIMAL128) : null;
                        Document produto = lotesDoProduto.get(0);
                        // Strings decimais preservam a precisão ao transportar os valores para JavaScript.
                        financeiroPorProduto.add(new Document("itemId", produto.getString("itemId"))
                                        .append("nome", produto.getString("nome"))
                                        .append("unidadeMedida", produto.getString("unidadeMedida"))
                                        .append("quantidadeDesperdicada", quantidade.toPlainString())
                                        .append("valorUnitario", valorUnitario == null ? null : valorUnitario.toPlainString())
                                        .append("valorTotalEstoque", valorTotalEstoque == null ? null : valorTotalEstoque.toPlainString())
                                        .append("valorPerdido", valorPerdido == null ? null : valorPerdido.toPlainString()));
                }

                return new Document("financeiroPorProduto", financeiroPorProduto);
        }

        private static BigDecimal decimalFinanceiro(Object valor) {
                if (valor instanceof Decimal128 decimal) {
                        return decimal.isFinite() ? decimal.bigDecimalValue() : null;
                }
                if (valor instanceof Number numero) {
                        try {
                                return new BigDecimal(numero.toString());
                        } catch (NumberFormatException erro) {
                                return null;
                        }
                }
                return null;
        }

        private static BigDecimal calcularPerdaLote(BigDecimal quantidade, BigDecimal inicial, BigDecimal custo) {
                // Multiplica antes da divisão para não arredondar um custo unitário intermediário.
                return quantidade.multiply(custo).divide(inicial, MathContext.DECIMAL128);
        }

        public Document consultarDesperdicioPorProduto() {
                List<Document> desperdiciosPorProduto = movimentacoes.aggregate(
                                List.of(

                                                Aggregates.match(
                                                                Filters.in("tipo", "VENCIMENTO", "AJUSTE_SAIDA")),

                                                Aggregates.lookup(
                                                                lotes.getNamespace().getCollectionName(),
                                                                "loteId", "_id", "lote"),

                                                Aggregates.unwind("$lote"),

                                                Aggregates.lookup(
                                                                itens.getNamespace().getCollectionName(), "lote.itemId",
                                                                "_id",
                                                                "produto"),

                                                Aggregates.unwind("$produto"),

                                                Aggregates.group(
                                                                "$produto._id",
                                                                Accumulators.first("nome", "$produto.nome"),
                                                                Accumulators.first("unidadeMedida",
                                                                                "$produto.unidadeMedida"),
                                                                Accumulators.sum("quantidadeTotal", "$quantidade")),

                                                Aggregates.project(
                                                                Projections.fields(
                                                                                Projections.excludeId(),
                                                                                Projections.computed("itemId",
                                                                                                new Document("$toString",
                                                                                                                "$_id")),
                                                                                Projections.include(
                                                                                                "nome",
                                                                                                "unidadeMedida",
                                                                                                "quantidadeTotal")))))
                                .into(new ArrayList<>());

                return new Document(
                                "desperdiciosPorProduto",
                                desperdiciosPorProduto);
        }

        public Document consultaDesperdicioPorMotivo() {
                List<Document> desperdicioPorMotivo = movimentacoes.aggregate(
                                List.of(
                                                Aggregates.match(
                                                                Filters.in("tipo", "VENCIMENTO", "AJUSTE_SAIDA")),

                                                Aggregates.group(
                                                                "$motivo", Accumulators.sum("totalOcorrencias", 1)),

                                                Aggregates.sort(
                                                                Sorts.descending("totalOcorrencias")),

                                                Aggregates.project(
                                                                Projections.fields(
                                                                                Projections.excludeId(),
                                                                                Projections.computed("motivo", "$_id"),
                                                                                Projections.include(
                                                                                                "totalOcorrencias")))

                                )).into(new ArrayList<>());

                return new Document(
                                "desperdiciosPorMotivo",
                                desperdicioPorMotivo);
        }

        public Document consultaDesperdicioPorPeriodo(LocalDate inicio, LocalDate fim) {
                ZoneId fuso = ZoneId.of("America/Sao_Paulo");

                Date dataInicio;
                Date dataFim;
                try {
                        dataInicio = Date.from(inicio.atStartOfDay(fuso).toInstant());
                        dataFim = Date.from(fim.plusDays(1).atStartOfDay(fuso).toInstant());
                } catch (DateTimeException | ArithmeticException | IllegalArgumentException erro) {
                        throw new IllegalArgumentException("Intervalo de datas fora do limite permitido.", erro);
                }

                Document agrupamentoData = new Document(
                                "$dateToString",
                                new Document("format", "%Y-%m-%d").append("date", "$dataMovimentacao")
                                                .append("timezone", "America/Sao_Paulo"));

                List<Document> desperdicioPorPeriodo = movimentacoes.aggregate(
                                List.of(
                                                Aggregates.match(
                                                                Filters.and(
                                                                                Filters.in("tipo", "VENCIMENTO",
                                                                                                "AJUSTE_SAIDA"),
                                                                                Filters.gte("dataMovimentacao",
                                                                                                dataInicio),
                                                                                Filters.lt("dataMovimentacao",
                                                                                                dataFim))),

                                                Aggregates.group(
                                                                agrupamentoData,
                                                                Accumulators.sum("totalOcorrencias", 1)),

                                                Aggregates.sort(Sorts.ascending("_id")),

                                                Aggregates.project(
                                                                Projections.fields(
                                                                                Projections.excludeId(),
                                                                                Projections.computed("data", "$_id"),
                                                                                Projections.include(
                                                                                                "totalOcorrencias")))))
                                .into(new ArrayList<>());

                return new Document(
                                "desperdicioPorPeriodo",
                                desperdicioPorPeriodo);

        }

        public Document consultaIndicadoresGerais() {
                long totalRegistrados = itens.countDocuments(Filters.eq("status", "ativo"));

                long totalDesperdicios = movimentacoes.countDocuments(
                                Filters.in("tipo", "VENCIMENTO", "AJUSTE_SAIDA"));

                Document resultado = consultaDesperdicioPorMotivo();
                List<Document> motivos = resultado.getList("desperdiciosPorMotivo", Document.class);
                String motivoFrequente = motivos.isEmpty() ? null : motivos.get(0).getString("motivo");

                Decimal128 perdasFinanceiras = calcularPerdasFinanceiras();

                return new Document().append("registrados", totalRegistrados).append("desperdicados",
                                totalDesperdicios).append("motivoMaisFrequente", motivoFrequente)
                                .append("perdasFinanceiras", perdasFinanceiras);
        }

        private Decimal128 calcularPerdasFinanceiras() {
                List<Document> desperdiciosPorLote = movimentacoes.aggregate(List.of(
                                Aggregates.match(Filters.in("tipo", "VENCIMENTO", "AJUSTE_SAIDA")),
                                Aggregates.group("$loteId",
                                                Accumulators.sum("quantidadeDesperdicada", "$quantidade")),
                                Aggregates.lookup(lotes.getNamespace().getCollectionName(), "_id", "_id", "lote"),
                                Aggregates.unwind("$lote"),
                                Aggregates.project(Projections.fields(
                                                Projections.excludeId(),
                                                Projections.computed("quantidadeInicial", "$lote.quantidadeInicial"),
                                                Projections.computed("custoTotalAquisicao", "$lote.custoTotalAquisicao"),
                                                Projections.include("quantidadeDesperdicada")))))
                                .into(new ArrayList<>());

                BigDecimal total = BigDecimal.ZERO;
                for (Document lote : desperdiciosPorLote) {
                        BigDecimal quantidade = decimalFinanceiro(lote.get("quantidadeDesperdicada"));
                        BigDecimal inicial = decimalFinanceiro(lote.get("quantidadeInicial"));
                        BigDecimal custo = decimalFinanceiro(lote.get("custoTotalAquisicao"));
                        if (quantidade != null && inicial != null && inicial.signum() > 0
                                        && custo != null && custo.signum() >= 0) {
                                total = total.add(calcularPerdaLote(quantidade, inicial, custo));
                        }
                }

                // Mantém o contrato BSON Decimal128, sem arredondamento monetário para centavos.
                return new Decimal128(total.round(MathContext.DECIMAL128));
        }

        public Document consultarEvolucaoMensal() {

                ZoneId fuso = ZoneId.of("America/Sao_Paulo");

                YearMonth mesAtual = YearMonth.now(fuso);
                YearMonth primeiroMes = mesAtual.minusMonths(5);

                Date inicio = Date.from(
                                primeiroMes.atDay(1).atStartOfDay(fuso).toInstant());

                Date fim = Date.from(
                                mesAtual.plusMonths(1).atDay(1)
                                                .atStartOfDay(fuso).toInstant());

                Document agrupamentoMes = new Document(
                                "$dateToString",
                                new Document("format", "%Y-%m")
                                                .append("date", "$dataMovimentacao")
                                                .append("timezone", "America/Sao_Paulo"));

                List<Document> resultados = movimentacoes.aggregate(
                                List.of(
                                                Aggregates.match(
                                                                Filters.and(
                                                                                Filters.in("tipo", "VENCIMENTO",
                                                                                                "AJUSTE_SAIDA"),
                                                                                Filters.gte("dataMovimentacao", inicio),
                                                                                Filters.lt("dataMovimentacao", fim))),

                                                Aggregates.group(
                                                                agrupamentoMes,
                                                                Accumulators.sum("totalOcorrencias", 1)),

                                                Aggregates.sort(Sorts.ascending("_id"))))
                                .into(new ArrayList<>());

                Map<String, Long> ocorrenciasPorMes = new HashMap<>();

                for (Document resultado : resultados) {
                        ocorrenciasPorMes.put(
                                        resultado.getString("_id"),
                                        resultado.get("totalOcorrencias", Number.class).longValue());
                }

                List<Document> evolucaoMensal = new ArrayList<>();

                for (int i = 0; i < 6; i++) {
                        String mes = primeiroMes.plusMonths(i).toString();

                        evolucaoMensal.add(
                                        new Document("mes", mes)
                                                        .append(
                                                                        "totalOcorrencias",
                                                                        ocorrenciasPorMes.getOrDefault(mes, 0L)));
                }

                return new Document("evolucaoMensal", evolucaoMensal);
        }
}
