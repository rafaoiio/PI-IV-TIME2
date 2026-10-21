package service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;

import org.bson.Document;
import org.bson.types.Decimal128;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DashboardService {

    private final MongoCollection<Document> itens;
    private final MongoCollection<Document> lotes;

    public DashboardService(MongoCollection<Document> it, MongoCollection<Document> lt) {
        this.itens = it;
        this.lotes = lt;
    }

    // Consulta os indicadores e prepara os dados do resumo do Dashboard.
    public Document consultarResumo() {
        long totalProdutos = itens.countDocuments();
        long totalLotes = lotes.countDocuments();

        ZoneId fuso = ZoneId.of("America/Sao_Paulo");
        LocalDate hoje = LocalDate.now(fuso);

        Date inicio = Date.from(
                hoje.atStartOfDay(fuso).toInstant());

        Date fim = Date.from(
                hoje.plusDays(7).atStartOfDay(fuso).toInstant());

        // consulta proxVencimento
        long lotesProximosVencimento = lotes.countDocuments(
                Filters.and(
                        Filters.gte("dataValidade", inicio),
                        Filters.lt("dataValidade", fim),
                        Filters.gt(
                                "quantidadeDisponivel", new Decimal128(BigDecimal.ZERO))));

        long produtosAtivos = itens.countDocuments(
                Filters.eq("status", "ativo"));
        long produtosInativos = itens.countDocuments(
                Filters.eq("status", "inativo"));

        List<Document> saldosPorProduto = lotes.aggregate(
                List.of(
                        Aggregates.match(
                                Filters.and(
                                        Filters.gte("dataValidade", inicio),
                                        Filters.gt(
                                                "quantidadeDisponivel", new Decimal128(BigDecimal.ZERO)))),

                        Aggregates.group(
                                "$itemId",
                                Accumulators.sum("quantidadeTotal", "$quantidadeDisponivel"))

                ))
                .into(new ArrayList<>());

        Document resumo = new Document()
                .append("totalProdutos", totalProdutos)
                .append("produtosAtivos", produtosAtivos)
                .append("produtosInativos", produtosInativos)
                .append("totalLotes", totalLotes)
                .append("lotesProximosVencimento", lotesProximosVencimento)
                .append("saldoPorProduto", saldosPorProduto);

        return resumo;
    }
}
