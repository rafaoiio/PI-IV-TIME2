
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import static com.mongodb.client.model.Filters.*;

public class ProdutosHandler implements HttpHandler {

    private final MongoCollection<Document> itens;
    private final MongoCollection<Document> categorias;
    private final MongoCollection<Document> lotes;
    private final MongoCollection<Document> movimentacoes;

    private static final Set<String> ORIGENS = Set.of(
        "http://localhost:5500",
        "http://127.0.0.1:5500"
    );

    public ProdutosHandler(
        MongoCollection<Document> itens,
        MongoCollection<Document> categorias,
        MongoCollection<Document> lotes,
        MongoCollection<Document> movimentacoes
    ) {
        this.itens = itens;
        this.categorias = categorias;
        this.lotes = lotes;
        this.movimentacoes = movimentacoes;
    }

    // =====================================================
    // ROTAS HTTP
    // =====================================================

    @Override
    public void handle(HttpExchange req) throws IOException {

        String origem = req.getRequestHeaders().getFirst("Origin");

        if (origem != null && !ORIGENS.contains(origem)) {
            responder(req, 403, "Origem nao permitida.");
            return;
        }

        if (origem != null) {
            req.getResponseHeaders().set(
                "Access-Control-Allow-Origin", origem
            );
            req.getResponseHeaders().set(
                "Access-Control-Allow-Headers", "Content-Type"
            );
            req.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET, POST, PUT, PATCH, DELETE, OPTIONS"
            );
            req.getResponseHeaders().set("Vary", "Origin");
        }

        req.getResponseHeaders().set("Cache-Control", "no-store");

        String metodo = req.getRequestMethod();
        String caminho = req.getRequestURI().getPath();

        if ("OPTIONS".equals(metodo)) {
            req.sendResponseHeaders(204, -1);
            req.close();
            return;
        }

        try {

            // CATEGORIAS
            if (caminho.equals("/api/categorias")) {

                if (metodo.equals("GET")) {
                    listarCategorias(req);
                } else {
                    responder(req, 405, "Metodo nao permitido.");
                }
                return;
            }

            // PRODUTOS - LISTAR E CADASTRAR
            if (caminho.equals("/api/produtos")) {

                switch (metodo) {
                    case "GET":
                        listarProdutos(req);
                        break;

                    case "POST":
                        cadastrarProduto(req);
                        break;

                    default:
                        responder(req, 405, "Metodo nao permitido.");
                }
                return;
            }

            // PRODUTOS COM ID
            if (caminho.startsWith("/api/produtos/")) {

                String restante = caminho.substring(
                    "/api/produtos/".length()
                );

                String[] partes = restante.split("/", -1);

                if (partes.length < 1 ||
                    partes.length > 2 ||
                    !ObjectId.isValid(partes[0])) {

                    responder(req, 400, "ID do produto invalido.");
                    return;
                }

                ObjectId id = new ObjectId(partes[0]);

                // /api/produtos/{id}
                if (partes.length == 1) {

                    switch (metodo) {
                        case "GET":
                            buscarProduto(req, id);
                            break;

                        case "PUT":
                            atualizarProduto(req, id);
                            break;

                        case "DELETE":
                            excluirProduto(req, id);
                            break;

                        default:
                            responder(req, 405, "Metodo nao permitido.");
                    }
                    return;
                }

                // /api/produtos/{id}/exclusao
                if (partes[1].equals("exclusao")
                    && metodo.equals("GET")) {

                    verificarExclusao(req, id);
                    return;
                }

                // /api/produtos/{id}/status
                if (partes[1].equals("status")
                    && metodo.equals("PATCH")) {

                    alterarStatus(req, id);
                    return;
                }
            }

            responder(req, 404, "Rota nao encontrada.");

        } catch (IllegalArgumentException erro) {

            responder(req, 400, erro.getMessage());

        } catch (MongoException erro) {

            System.err.println(
                "Erro MongoDB: " + erro.getMessage()
            );

            responder(req, 500, "Erro ao acessar o banco.");

        } catch (RuntimeException erro) {

            System.err.println(
                "Erro interno: " + erro.getMessage()
            );

            responder(req, 500, "Erro interno do servidor.");
        }
    }

    // =====================================================
    // CATEGORIAS
    // =====================================================

    private void listarCategorias(HttpExchange req)
        throws IOException {

        List<Document> lista = new ArrayList<>();

        for (Document categoria : categorias.find()) {

            String nome = categoria.getString("nome");

            if (nome == null || nome.isBlank()) {
                continue;
            }

            lista.add(new Document()
                .append(
                    "id",
                    categoria.getObjectId("_id").toHexString()
                )
                .append("nome", nome));
        }

        responderJSON(
            req,
            200,
            new Document("categorias", lista)
        );
    }

    private static String gerarCodigo(String nome) {

        String texto = Normalizer.normalize(
            nome.trim().toLowerCase(Locale.ROOT),
            Normalizer.Form.NFD
        );

        return texto
            .replaceAll("\\p{M}", "")
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
    }

    private ObjectId buscarOuCriarCategoria(String nome) {

        String codigo = gerarCodigo(nome);

        if (codigo.isEmpty()) {
            throw new IllegalArgumentException(
                "Categoria invalida."
            );
        }

        Document categoria = categorias.find(
            eq("codigo", codigo)
        ).first();

        if (categoria == null) {
            categoria = categorias.find(
                regex(
                    "nome",
                    "^" + Pattern.quote(nome) + "$",
                    "i"
                )
            ).first();
        }

        if (categoria == null) {

            categoria = new Document()
                .append("nome", nome)
                .append("codigo", codigo)
                .append("criadoEm", new Date());

            categorias.insertOne(categoria);
        }

        return categoria.getObjectId("_id");
    }

    private String buscarNomeCategoria(Document produto) {

        ObjectId categoriaId =
            produto.getObjectId("categoriaId");

        if (categoriaId == null) {
            return "Sem categoria";
        }

        Document categoria = categorias.find(
            eq("_id", categoriaId)
        ).first();

        if (categoria == null) {
            return "Sem categoria";
        }

        return categoria.getString("nome");
    }

    // =====================================================
    // LEITURA E VALIDACAO DE DADOS
    // =====================================================

    private Document lerDados(HttpExchange req)
        throws IOException {

        byte[] conteudo = req.getRequestBody()
            .readNBytes(8193);

        if (conteudo.length > 8192) {
            throw new IllegalArgumentException(
                "Dados muito grandes."
            );
        }

        try {
            return Document.parse(
                new String(conteudo, StandardCharsets.UTF_8)
            );
        } catch (RuntimeException erro) {
            throw new IllegalArgumentException("JSON invalido.");
        }
    }

    private static String obterTexto(
        Document documento,
        String campo
    ) {

        Object valor = documento.get(campo);

        return valor instanceof String
            ? ((String) valor).trim()
            : "";
    }

    private Document validarProduto(Document dados) {

        String nome = obterTexto(
            dados, "nomeProduto"
        );

        String categoria = obterTexto(
            dados, "categoria"
        ).replaceAll("\\s+", " ");

        String quantidadeTexto = obterTexto(
            dados, "quantidadeUnidade"
        );

        String unidade = obterTexto(
            dados, "unidadeMedida"
        ).toLowerCase(Locale.ROOT);

        String status = obterTexto(
            dados, "status"
        ).toLowerCase(Locale.ROOT);

        String observacao = obterTexto(
            dados, "observacao"
        );

        if (nome.isEmpty() || nome.length() > 150) {
            throw new IllegalArgumentException(
                "Nome do produto invalido."
            );
        }

        if (categoria.isEmpty() ||
            categoria.length() > 100) {

            throw new IllegalArgumentException(
                "Categoria invalida."
            );
        }

        BigDecimal quantidade;

        try {
            quantidade = new BigDecimal(
                quantidadeTexto.replace(",", ".")
            );
        } catch (NumberFormatException erro) {
            throw new IllegalArgumentException(
                "Quantidade da unidade invalida."
            );
        }

        if (quantidade.signum() <= 0 ||
            quantidade.compareTo(
                BigDecimal.valueOf(Double.MAX_VALUE)
            ) > 0) {

            throw new IllegalArgumentException(
                "Quantidade da unidade invalida."
            );
        }

        if (!Set.of("kg", "g", "l", "ml", "un")
            .contains(unidade)) {

            throw new IllegalArgumentException(
                "Unidade de medida invalida."
            );
        }

        if (!Set.of("ativo", "inativo")
            .contains(status)) {

            throw new IllegalArgumentException(
                "Status invalido."
            );
        }

        if (observacao.isEmpty() ||
            observacao.length() > 1000) {

            throw new IllegalArgumentException(
                "Observacao invalida."
            );
        }

        ObjectId categoriaId =
            buscarOuCriarCategoria(categoria);

        return new Document()
            .append("nome", nome)
            .append("categoriaId", categoriaId)
            .append(
                "quantidadeUnidade",
                quantidade.doubleValue()
            )
            .append("unidadeMedida", unidade)
            .append("status", status)
            .append("observacao", observacao);
    }

    // =====================================================
    // CADASTRAR PRODUTO
    // =====================================================

    private void cadastrarProduto(HttpExchange req)
        throws IOException {

        Document dados = lerDados(req);

        Document produto = validarProduto(dados);

        produto.append("criadoEm", new Date());

        itens.insertOne(produto);

        responderJSON(
            req,
            201,
            new Document()
                .append(
                    "mensagem",
                    "Produto cadastrado com sucesso!"
                )
                .append(
                    "id",
                    produto.getObjectId("_id").toHexString()
                )
        );
    }

    // =====================================================
    // BUSCAR PRODUTO PELO ID - ENGRENAGEM
    // =====================================================

    private void buscarProduto(
        HttpExchange req,
        ObjectId id
    ) throws IOException {

        Document produto = itens.find(
            eq("_id", id)
        ).first();

        if (produto == null) {
            responder(req, 404, "Produto nao encontrado.");
            return;
        }

        Document resposta = new Document()
            .append("id", id.toHexString())
            .append(
                "nome",
                produto.getString("nome")
            )
            .append(
                "categoria",
                buscarNomeCategoria(produto)
            )
            .append(
                "quantidadeUnidade",
                obterDecimal(
                    produto,
                    "quantidadeUnidade"
                ).toPlainString()
            )
            .append(
                "unidadeMedida",
                produto.getString("unidadeMedida")
            )
            .append(
                "status",
                produto.getString("status")
            )
            .append(
                "observacao",
                produto.getString("observacao")
            );

        responderJSON(
            req,
            200,
            new Document("produto", resposta)
        );
    }

    // =====================================================
    // EDITAR PRODUTO - SALVAR ALTERACOES
    // =====================================================

    private void atualizarProduto(
        HttpExchange req,
        ObjectId id
    ) throws IOException {

        Document existente = itens.find(
            eq("_id", id)
        ).first();

        if (existente == null) {
            responder(req, 404, "Produto nao encontrado.");
            return;
        }

        Document dados = lerDados(req);

        Document alteracoes = validarProduto(dados);

        alteracoes.append("atualizadoEm", new Date());

        UpdateResult resultado = itens.updateOne(
            eq("_id", id),
            new Document("$set", alteracoes)
        );

        if (resultado.getMatchedCount() == 0) {
            responder(req, 404, "Produto nao encontrado.");
            return;
        }

        responder(
            req,
            200,
            "Produto atualizado com sucesso!"
        );
    }

    // =====================================================
    // VERIFICAR MOVIMENTACOES DE PRODUTOS E LOTES
    // =====================================================

    private boolean possuiMovimentacoes(ObjectId produtoId) {

        // Busca movimentacoes diretamente pelo produto.
        if (movimentacoes.countDocuments(
            or(
                eq("itemId", produtoId),
                eq("produtoId", produtoId)
            )
        ) > 0) {
            return true;
        }

        // Busca todos os lotes relacionados ao produto.
        List<ObjectId> idsLotes = new ArrayList<>();

        for (Document lote : lotes.find(
            eq("itemId", produtoId)
        )) {

            ObjectId loteId = lote.getObjectId("_id");

            if (loteId != null) {
                idsLotes.add(loteId);
            }
        }

        if (idsLotes.isEmpty()) {
            return false;
        }

        // Busca movimentacoes pelos IDs dos lotes.
        return movimentacoes.countDocuments(
            in("loteId", idsLotes)
        ) > 0;
    }

    private boolean possuiLotes(ObjectId produtoId) {

        return lotes.countDocuments(
            eq("itemId", produtoId)
        ) > 0;
    }

    // =====================================================
    // VERIFICAR SE PODE EXCLUIR - LIXEIRA
    // =====================================================

    private void verificarExclusao(
        HttpExchange req,
        ObjectId id
    ) throws IOException {

        Document produto = itens.find(
            eq("_id", id)
        ).first();

        if (produto == null) {
            responder(req, 404, "Produto nao encontrado.");
            return;
        }

        boolean temMovimentacoes =
            possuiMovimentacoes(id);

        boolean temLotes =
            possuiLotes(id);

        boolean podeExcluir =
            !temMovimentacoes && !temLotes;

        String mensagem;

        if (temMovimentacoes) {

            mensagem =
                "Este produto nao pode ser excluido, " +
                "pois possui movimentacoes registradas. " +
                "Deseja torna-lo inativo?";

        } else if (temLotes) {

            mensagem =
                "Este produto nao pode ser excluido, " +
                "pois possui lotes vinculados. " +
                "Deseja torna-lo inativo?";

        } else {

            mensagem =
                "Tem certeza de que deseja excluir este produto?";
        }

        responderJSON(
            req,
            200,
            new Document()
                .append("podeExcluir", podeExcluir)
                .append(
                    "possuiMovimentacoes",
                    temMovimentacoes
                )
                .append("possuiLotes", temLotes)
                .append("mensagem", mensagem)
        );
    }

    // =====================================================
    // EXCLUIR PRODUTO
    // =====================================================

    private void excluirProduto(
        HttpExchange req,
        ObjectId id
    ) throws IOException {

        Document produto = itens.find(
            eq("_id", id)
        ).first();

        if (produto == null) {
            responder(req, 404, "Produto nao encontrado.");
            return;
        }

        // Confere novamente antes de excluir.
        // Impede exclusao de produtos com historico ou lotes.
        if (possuiMovimentacoes(id) || possuiLotes(id)) {

            responderJSON(
                req,
                409,
                new Document()
                    .append("podeExcluir", false)
                    .append(
                        "mensagem",
                        "Este produto nao pode ser excluido, " +
                        "pois possui movimentacoes ou lotes vinculados. " +
                        "Deseja torna-lo inativo?"
                    )
            );
            return;
        }

        DeleteResult resultado = itens.deleteOne(
            eq("_id", id)
        );

        if (resultado.getDeletedCount() == 0) {
            responder(req, 404, "Produto nao encontrado.");
            return;
        }

        responder(
            req,
            200,
            "Produto excluido com sucesso!"
        );
    }

    // =====================================================
    // ALTERAR STATUS - TORNAR INATIVO
    // =====================================================

    private void alterarStatus(
        HttpExchange req,
        ObjectId id
    ) throws IOException {

        Document produto = itens.find(
            eq("_id", id)
        ).first();

        if (produto == null) {
            responder(req, 404, "Produto nao encontrado.");
            return;
        }

        Document dados = lerDados(req);

        String status = obterTexto(
            dados,
            "status"
        ).toLowerCase(Locale.ROOT);

        if (!Set.of("ativo", "inativo")
            .contains(status)) {

            responder(req, 400, "Status invalido.");
            return;
        }

        UpdateResult resultado = itens.updateOne(
            eq("_id", id),
            new Document(
                "$set",
                new Document()
                    .append("status", status)
                    .append("atualizadoEm", new Date())
            )
        );

        if (resultado.getMatchedCount() == 0) {
            responder(req, 404, "Produto nao encontrado.");
            return;
        }

        responder(
            req,
            200,
            status.equals("inativo")
                ? "Produto inativado com sucesso!"
                : "Status atualizado com sucesso!"
        );
    }

    // =====================================================
    // LISTAR PRODUTOS
    // =====================================================

    private void listarProdutos(HttpExchange req)
        throws IOException {

        List<Document> listaProdutos = new ArrayList<>();

        for (Document produto : itens.find()) {

            ObjectId produtoId =
                produto.getObjectId("_id");

            String nomeCategoria =
                buscarNomeCategoria(produto);

            BigDecimal quantidadeUnidade =
                obterDecimal(produto, "quantidadeUnidade");

            BigDecimal quantidadeTotal =
                BigDecimal.ZERO;

            BigDecimal custoEstoque =
                BigDecimal.ZERO;

            BigDecimal quantidadeComPreco =
                BigDecimal.ZERO;

            Date validadeMaisProxima = null;

            // Consulta todos os lotes do produto.
            for (Document lote : lotes.find(
                eq("itemId", produtoId)
            )) {

                BigDecimal quantidadeDisponivel =
                    obterDecimal(
                        lote,
                        "quantidadeDisponivel"
                    );

                BigDecimal quantidadeInicial =
                    obterDecimal(
                        lote,
                        "quantidadeInicial"
                    );

                BigDecimal custoTotal =
                    obterDecimal(
                        lote,
                        "custoTotalAquisicao"
                    );

                // Considera somente estoque disponivel.
                if (quantidadeDisponivel.signum() <= 0) {
                    continue;
                }

                quantidadeTotal = quantidadeTotal.add(
                    quantidadeDisponivel
                );

                // PRECO MEDIO PONDERADO
                if (
                    quantidadeInicial.signum() > 0 &&
                    lote.get("custoTotalAquisicao") != null
                ) {

                    BigDecimal precoDoLote = custoTotal.divide(
                        quantidadeInicial,
                        10,
                        RoundingMode.HALF_UP
                    );

                    custoEstoque = custoEstoque.add(
                        precoDoLote.multiply(
                            quantidadeDisponivel
                        )
                    );

                    quantidadeComPreco = quantidadeComPreco.add(
                        quantidadeDisponivel
                    );
                }

                // VALIDADE MAIS PROXIMA
                Date validade = lote.getDate("dataValidade");

                if (validade != null) {

                    if (
                        validadeMaisProxima == null ||
                        validade.before(validadeMaisProxima)
                    ) {
                        validadeMaisProxima = validade;
                    }
                }
            }

            // PRECO UNITARIO
            String precoUnitario = null;

            if (quantidadeComPreco.signum() > 0) {

                precoUnitario = custoEstoque.divide(
                    quantidadeComPreco,
                    2,
                    RoundingMode.HALF_UP
                ).toPlainString();
            }

            // DATA DE VALIDADE
            String dataValidade = null;

            if (validadeMaisProxima != null) {

                dataValidade = validadeMaisProxima
                    .toInstant()
                    .toString();
            }

            // DOCUMENTO ENVIADO AO FRONTEND
            Document resultado = new Document()
                .append(
                    "id",
                    produtoId.toHexString()
                )
                .append(
                    "nome",
                    produto.getString("nome")
                )
                .append(
                    "categoria",
                    nomeCategoria
                )
                .append(
                    "quantidadeUnidade",
                    quantidadeUnidade.toPlainString()
                )
                .append(
                    "unidadeMedida",
                    produto.getString("unidadeMedida")
                )
                .append(
                    "quantidadeTotal",
                    quantidadeTotal.toPlainString()
                )
                .append(
                    "precoUnitario",
                    precoUnitario
                )
                .append(
                    "dataValidade",
                    dataValidade
                )
                .append(
                    "status",
                    produto.getString("status")
                )
                .append(
                    "observacao",
                    produto.getString("observacao")
                );

            listaProdutos.add(resultado);
        }

        responderJSON(
            req,
            200,
            new Document("produtos", listaProdutos)
        );
    }

    // =====================================================
    // CONVERSAO DOS NUMEROS DO MONGODB
    // =====================================================

    private static BigDecimal obterDecimal(
        Document documento,
        String campo
    ) {

        Object valor = documento.get(campo);

        if (valor instanceof Decimal128) {
            return ((Decimal128) valor).bigDecimalValue();
        }

        if (valor instanceof Number) {
            return new BigDecimal(valor.toString());
        }

        if (valor instanceof String) {

            try {
                return new BigDecimal((String) valor);
            } catch (NumberFormatException erro) {
                return BigDecimal.ZERO;
            }
        }

        return BigDecimal.ZERO;
    }

    // =====================================================
    // RESPOSTAS HTTP
    // =====================================================

    private static void responder(
        HttpExchange req,
        int status,
        String mensagem
    ) throws IOException {

        responderJSON(
            req,
            status,
            new Document("mensagem", mensagem)
        );
    }

    private static void responderJSON(
        HttpExchange req,
        int status,
        Document dados
    ) throws IOException {

        byte[] resposta = dados.toJson()
            .getBytes(StandardCharsets.UTF_8);

        req.getResponseHeaders().set(
            "Content-Type",
            "application/json; charset=UTF-8"
        );

        req.sendResponseHeaders(
            status,
            resposta.length
        );

        try (OutputStream saida = req.getResponseBody()) {
            saida.write(resposta);
        }
    }
}
