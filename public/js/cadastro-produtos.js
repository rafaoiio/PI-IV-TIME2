document.addEventListener("DOMContentLoaded", () => {

    const API = "http://localhost:8080/api";

    const formulario =
        document.getElementById("formCadastroProduto");

    const campoCategoria =
        document.getElementById("categoria");

    const listaCategorias =
        document.getElementById("listaCategorias");

    const botaoSalvar =
        document.getElementById("botaoSalvar");

    const mensagem =
        document.getElementById("mensagemFormulario");

    // Verifica se existe um ID na URL.
    const parametros = new URLSearchParams(window.location.search);

    const produtoId = parametros.get("id");

    const editando = Boolean(produtoId);

    function mostrarErro(texto) {
        mensagem.textContent = texto;
        mensagem.classList.remove("hidden");
    }

    // Lê as respostas enviadas pelo backend.
    async function lerResposta(resposta) {

        const texto = await resposta.text();

        let dados = {};

        if (texto) {
            try {
                dados = JSON.parse(texto);
            } catch {
                dados = { mensagem: texto };
            }
        }

        if (!resposta.ok) {
            throw new Error(
                dados.mensagem ||
                dados.erro ||
                `Erro HTTP ${resposta.status}`
            );
        }

        return dados;
    }

    // Busca as categorias cadastradas no MongoDB.
    async function carregarCategorias() {

        try {

            const resposta = await fetch(`${API}/categorias`);

            const dados = await lerResposta(resposta);

            const categorias = Array.isArray(dados)
                ? dados
                : dados.categorias || [];

            listaCategorias.replaceChildren();

            categorias.forEach((categoria) => {

                const opcao = document.createElement("option");

                opcao.value = typeof categoria === "string"
                    ? categoria
                    : categoria.nome || "";

                listaCategorias.appendChild(opcao);
            });

        } catch (erro) {
            console.error("Erro ao buscar categorias:", erro);
        }
    }

    // Busca os dados do produto selecionado para edição.
    async function carregarProduto() {

        botaoSalvar.disabled = true;

        try {

            const resposta = await fetch(
                `${API}/produtos/${encodeURIComponent(produtoId)}`
            );

            const dados = await lerResposta(resposta);

            const produto = dados.produto || dados;

            document.getElementById("nomeProduto").value =
                produto.nomeProduto || produto.nome || "";

            campoCategoria.value =
                typeof produto.categoria === "object"
                    ? produto.categoria?.nome || ""
                    : produto.categoria || "";

            document.getElementById("quantidadeUnidade").value =
                produto.quantidadeUnidade ?? "";

            document.getElementById("unidadeMedida").value =
                String(produto.unidadeMedida || "").toLowerCase();

            document.getElementById("status").value =
                String(produto.status || "ativo").toLowerCase();

            document.getElementById("observacao").value =
                produto.observacao || "";

        } catch (erro) {

            mostrarErro(
                `Não foi possível carregar o produto: ${erro.message}`
            );

        } finally {
            botaoSalvar.disabled = false;
        }
    }

    // Configura a tela para o modo de edição.
    if (editando) {

        document.title =
            "Editar Produto | Sistema de Gestão e Redução de Desperdício";

        document.getElementById("caminhoPagina").textContent =
            "/ Editar Produto";

        document.getElementById("tituloFormulario").textContent =
            "Editar produto";

        botaoSalvar.textContent = "Salvar alterações";

        carregarProduto();
    }

    // Botão cancelar.
    document.getElementById("botaoCancelar")
        .addEventListener("click", () => {

            window.location.href = "todos-produtos.html";
        });

    carregarCategorias();

    campoCategoria.addEventListener(
        "focus",
        carregarCategorias
    );

    // Cadastro ou atualização.
    formulario.addEventListener("submit", async (evento) => {

        evento.preventDefault();

        mensagem.classList.add("hidden");

        const produto = {

            nomeProduto:
                document.getElementById("nomeProduto").value.trim(),

            categoria:
                campoCategoria.value.trim(),

            quantidadeUnidade:
                document.getElementById("quantidadeUnidade").value.trim(),

            unidadeMedida:
                document.getElementById("unidadeMedida").value,

            status:
                document.getElementById("status").value,

            observacao:
                document.getElementById("observacao").value.trim()
        };

        botaoSalvar.disabled = true;

        botaoSalvar.textContent = editando
            ? "Salvando..."
            : "Cadastrando...";

        try {

            const url = editando
                ? `${API}/produtos/${encodeURIComponent(produtoId)}`
                : `${API}/produtos`;

            const metodo = editando ? "PUT" : "POST";

            const resposta = await fetch(url, {

                method: metodo,

                headers: {
                    "Content-Type": "application/json"
                },

                body: JSON.stringify(produto)
            });

            const dados = await lerResposta(resposta);

            alert(
                dados.mensagem ||
                (editando
                    ? "Produto atualizado com sucesso!"
                    : "Produto cadastrado com sucesso!")
            );

            if (editando) {

                window.location.href = "todos-produtos.html";

            } else {

                formulario.reset();

                await carregarCategorias();
            }

        } catch (erro) {

            console.error("Erro ao salvar produto:", erro);

            mostrarErro(erro.message);

        } finally {

            botaoSalvar.disabled = false;

            botaoSalvar.textContent = editando
                ? "Salvar alterações"
                : "Cadastrar novo produto";
        }
    });

});
