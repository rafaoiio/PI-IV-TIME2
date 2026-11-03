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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
                Date inicioHoje = Date.from(hoje.atStartOfDay(fuso).toInstant());

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
}
