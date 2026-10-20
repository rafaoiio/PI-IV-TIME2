document.addEventListener("DOMContentLoaded", () => {

    const formulario =
        document.getElementById("formCadastroProduto");

    const campoCategoria =
        document.getElementById("categoria");

    const listaCategorias =
        document.getElementById("listaCategorias");

    const API = "http://localhost:8080/api";

    // Busca as categorias existentes no MongoDB.
    async function carregarCategorias() {

        try {
            const resposta = await fetch(
                `${API}/categorias`
            );

            if (!resposta.ok) {
                throw new Error("Erro ao carregar categorias.");
            }

            const dados = await resposta.json();

            // Limpa as opcoes anteriores.
            listaCategorias.replaceChildren();

            // Adiciona as categorias cadastradas.
            dados.categorias.forEach((categoria) => {

                const opcao = document.createElement("option");

                opcao.value = categoria.nome;

                listaCategorias.appendChild(opcao);
            });

        } catch (erro) {
            console.error("Erro ao buscar categorias:", erro);
        }
    }

    // Carrega as categorias ao abrir a pagina.
    carregarCategorias();

    // Atualiza as categorias ao entrar no campo.
    campoCategoria.addEventListener(
        "focus",
        carregarCategorias
    );

    // Cadastro do produto.
    formulario.addEventListener("submit", async (evento) => {

        evento.preventDefault();

        const produto = {
            nomeProduto:
                document.getElementById("nomeProduto").value.trim(),

            categoria:
                campoCategoria.value.trim(),

            unidadeMedida:
                document.getElementById("unidadeMedida").value,

            status:
                document.getElementById("status").value,

            observacao:
                document.getElementById("observacao").value.trim()
        };

        const botao = formulario.querySelector(
            'button[type="submit"]'
        );

        botao.disabled = true;
        botao.textContent = "Cadastrando...";

        try {

            const resposta = await fetch(
                `${API}/produtos`,
                {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify(produto)
                }
            );

            const resultado = await resposta.json();

            if (!resposta.ok) {
                throw new Error(
                    resultado.mensagem ||
                    "Erro ao cadastrar produto."
                );
            }

            alert(resultado.mensagem);

            // Limpa o formulario.
            formulario.reset();

            // Atualiza as categorias, incluindo a nova.
            await carregarCategorias();

        } catch (erro) {

            console.error("Erro:", erro);
            alert(erro.message);

        } finally {

            botao.disabled = false;
            botao.textContent = "Cadastrar novo produto";
        }
    });
});
