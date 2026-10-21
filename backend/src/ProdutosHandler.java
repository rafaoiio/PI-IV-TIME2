import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Filters.*;

public class ProdutosHandler implements HttpHandler {

    private final MongoCollection<Document> itens;
    private final MongoCollection<Document> categorias;

    private static final Set<String> ORIGENS = Set.of(
        "http://localhost:5500",
        "http://127.0.0.1:5500"
    );

    public ProdutosHandler(
        MongoCollection<Document> itens,
        MongoCollection<Document> categorias
    ) {
        this.itens = itens;
        this.categorias = categorias;
    }

    @Override
    public void handle(HttpExchange requisicao)
            throws IOException {

        String origem = requisicao.getRequestHeaders()
                .getFirst("Origin");

        if (origem != null && !ORIGENS.contains(origem)) {
            responder(requisicao, 403, "Origem nao permitida.");
            return;
        }

        if (origem != null) {
            requisicao.getResponseHeaders().set(
                "Access-Control-Allow-Origin", origem
            );

            requisicao.getResponseHeaders().set(
                "Access-Control-Allow-Headers", "Content-Type"
            );

            requisicao.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET, POST, OPTIONS"
            );

            requisicao.getResponseHeaders().set(
                "Vary", "Origin"
            );
        }

        String metodo = requisicao.getRequestMethod();
        String caminho = requisicao.getRequestURI().getPath();

        if (metodo.equals("OPTIONS")) {
            requisicao.sendResponseHeaders(204, -1);
            requisicao.close();
            return;
        }

        if (caminho.equals("/api/categorias")
                && metodo.equals("GET")) {
            listarCategorias(requisicao);
            return;
        }

        if (caminho.equals("/api/produtos")) {

            if (metodo.equals("GET")) {
                listarProdutos(requisicao);
                return;
            }

            if (metodo.equals("POST")) {
                cadastrarProduto(requisicao);
                return;
            }
        }

        responder(requisicao, 405, "Operacao nao permitida.");
    }

    // Converte o nome em um codigo padronizado.
    private static String gerarCodigo(String nome) {

        String texto = Normalizer.normalize(
            nome.trim().toLowerCase(java.util.Locale.ROOT),
            Normalizer.Form.NFD
        );

        return texto
            .replaceAll("\\p{M}", "")
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
    }

    // Lista as categorias existentes.
    private void listarCategorias(HttpExchange requisicao)
            throws IOException {

        try {
            List<Document> lista = new ArrayList<>();

            for (Document categoria : categorias.find()) {
                String nome = categoria.getString("nome");

                if (nome != null && !nome.isBlank()) {
                    lista.add(new Document()
                        .append("id",
                            categoria.getObjectId("_id").toHexString())
                        .append("nome", nome)
                    );
                }
            }

            responderJSON(
                requisicao,
                200,
                new Document("categorias", lista)
            );

        } catch (MongoException erro) {
            responder(
                requisicao, 500,
                "Erro ao buscar categorias."
            );
        }
    }

    // Cadastra um produto.
    private void cadastrarProduto(HttpExchange requisicao)
            throws IOException {

        byte[] conteudo = requisicao.getRequestBody()
                .readNBytes(8193);

        if (conteudo.length > 8192) {
            responder(requisicao, 413, "Dados muito grandes.");
            return;
        }

        Document dados;

        try {
            dados = Document.parse(
                new String(conteudo, StandardCharsets.UTF_8)
            );
        } catch (RuntimeException erro) {
            responder(requisicao, 400, "JSON invalido.");
            return;
        }

        String nome = obterTexto(dados, "nomeProduto").trim();

        String nomeCategoria =
            obterTexto(dados, "categoria")
                .trim()
                .replaceAll("\\s+", " ");

        String unidade =
            obterTexto(dados, "unidadeMedida").trim();

        String status =
            obterTexto(dados, "status").trim();

        String observacao =
            obterTexto(dados, "observacao").trim();

        if (nome.isEmpty() || nome.length() > 150) {
            responder(requisicao, 400, "Nome do produto invalido.");
            return;
        }

        if (nomeCategoria.isEmpty()
                || nomeCategoria.length() > 100) {
            responder(requisicao, 400, "Categoria invalida.");
            return;
        }

        String codigoCategoria = gerarCodigo(nomeCategoria);

        if (codigoCategoria.isEmpty()) {
            responder(requisicao, 400, "Categoria invalida.");
            return;
        }

        if (!Set.of("kg", "g", "l", "ml", "un")
                .contains(unidade)) {
            responder(requisicao, 400, "Unidade invalida.");
            return;
        }

        if (!Set.of("ativo", "inativo").contains(status)) {
            responder(requisicao, 400, "Status invalido.");
            return;
        }

        if (observacao.isEmpty()
                || observacao.length() > 1000) {
            responder(requisicao, 400, "Observacao invalida.");
            return;
        }

        try {

            // Procura primeiro pelo codigo da categoria.
            Document categoria = categorias.find(
                eq("codigo", codigoCategoria)
            ).first();

            // Tambem procura categorias antigas apenas pelo nome.
            if (categoria == null) {
                categoria = categorias.find(
                    regex(
                        "nome",
                        "^" + Pattern.quote(nomeCategoria) + "$",
                        "i"
                    )
                ).first();
            }

            // Se a categoria nao existir, cria uma nova.
            if (categoria == null) {

                categoria = new Document()
                    .append("nome", nomeCategoria)
                    .append("codigo", codigoCategoria)
                    .append("criadoEm", new Date());

                categorias.insertOne(categoria);
            }

            ObjectId categoriaId =
                categoria.getObjectId("_id");

            // Cria o documento do produto.
            Document produto = new Document()
                .append("nome", nome)
                .append("categoriaId", categoriaId)
                .append("unidadeMedida", unidade)
                .append("status", status)
                .append("observacao", observacao)
                .append("criadoEm", new Date());

            // Salva na colecao itens.
            itens.insertOne(produto);

            Document resposta = new Document()
                .append(
                    "mensagem",
                    "Produto cadastrado com sucesso!"
                )
                .append(
                    "id",
                    produto.getObjectId("_id").toHexString()
                )
                .append(
                    "categoriaId",
                    categoriaId.toHexString()
                );

            responderJSON(requisicao, 201, resposta);

        } catch (MongoException erro) {
            System.err.println(erro.getMessage());

            responder(
                requisicao, 500,
                "Erro ao acessar o banco de dados."
            );

        } catch (RuntimeException erro) {
            System.err.println(erro.getMessage());

            responder(
                requisicao, 500,
                "Erro interno ao cadastrar produto."
            );
        }
    }

    
    // Lista todos os produtos cadastrados no MongoDB.
    private void listarProdutos(HttpExchange requisicao)
            throws IOException {

        try {

            List<Document> listaProdutos = new ArrayList<>();

            // Busca os produtos da colecao itens.
            for (Document produto : itens.find()) {

                String nomeCategoria = "Sem categoria";

                // Recupera o ID da categoria do produto.
                ObjectId categoriaId =
                    produto.getObjectId("categoriaId");

                // Busca o nome da categoria no MongoDB.
                if (categoriaId != null) {

                    Document categoria = categorias.find(
                        eq("_id", categoriaId)
                    ).first();

                    if (categoria != null) {
                        nomeCategoria = categoria.getString("nome");
                    }
                }

                // Prepara as informacoes do produto.
                Document resultado = new Document()
                    .append(
                        "id",
                        produto.getObjectId("_id").toHexString()
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

                listaProdutos.add(resultado);
            }

            // Retorna os produtos em formato JSON.
            responderJSON(
                requisicao,
                200,
                new Document("produtos", listaProdutos)
            );

        } catch (MongoException erro) {

            System.err.println(
                "Erro ao listar produtos: " + erro.getMessage()
            );

            responder(
                requisicao,
                500,
                "Erro ao buscar produtos."
            );

        } catch (RuntimeException erro) {

            System.err.println(
                "Erro ao processar produtos: " + erro.getMessage()
            );

            responder(
                requisicao,
                500,
                "Erro interno ao listar produtos."
            );
        }
    }


    private static String obterTexto(
        Document dados,
        String campo
    ) {
        Object valor = dados.get(campo);

        return valor instanceof String
            ? (String) valor
            : "";
    }

    private static void responder(
        HttpExchange requisicao,
        int status,
        String mensagem
    ) throws IOException {

        responderJSON(
            requisicao,
            status,
            new Document("mensagem", mensagem)
        );
    }

    private static void responderJSON(
        HttpExchange requisicao,
        int status,
        Document dados
    ) throws IOException {

        byte[] resposta = dados.toJson()
            .getBytes(StandardCharsets.UTF_8);

        requisicao.getResponseHeaders().set(
            "Content-Type",
            "application/json; charset=UTF-8"
        );

        requisicao.sendResponseHeaders(
            status,
            resposta.length
        );

        try (OutputStream saida =
                requisicao.getResponseBody()) {
            saida.write(resposta);
        }
    }
}
