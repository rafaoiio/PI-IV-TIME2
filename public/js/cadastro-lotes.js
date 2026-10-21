
document.addEventListener("DOMContentLoaded", () => {

    const formulario =
        document.getElementById("formCadastroLote");

    const campoProduto =
        document.getElementById("produto");

    const campoQuantidade =
        document.getElementById("quantidadeRecebida");

    const campoValorUnitario =
        document.getElementById("valorUnitario");

    const campoCustoTotal =
        document.getElementById("custoTotalAquisicao");

    const API = "http://localhost:8080/api";

    // Carrega os produtos cadastrados no MongoDB
    async function carregarProdutos() {

        campoProduto.replaceChildren();

        const opcaoInicial = document.createElement("option");

        opcaoInicial.value = "";
        opcaoInicial.textContent = "Carregando produtos...";

        campoProduto.appendChild(opcaoInicial);
        campoProduto.disabled = true;

        try {

            const resposta = await fetch(
                `${API}/produtos`,
                {
                    method: "GET",
                    headers: {
                        "Accept": "application/json"
                    }
                }
            );

            if (!resposta.ok) {
                throw new Error(
                    "Nao foi possivel carregar os produtos."
                );
            }

            const dados = await resposta.json();

            if (!Array.isArray(dados.produtos)) {
                throw new Error(
                    "Resposta de produtos invalida."
                );
            }

            campoProduto.replaceChildren();

            const opcaoPadrao = document.createElement("option");

            opcaoPadrao.value = "";
            opcaoPadrao.textContent =
                "Selecione um produto cadastrado";

            campoProduto.appendChild(opcaoPadrao);

            // Adiciona os produtos na lista
            dados.produtos.forEach((produto) => {

                if (!produto.id || !produto.nome) {
                    return;
                }

                const opcao = document.createElement("option");

                opcao.value = produto.id;
                opcao.textContent = produto.nome;

                campoProduto.appendChild(opcao);
            });

            campoProduto.disabled = false;

        } catch (erro) {

            console.error(
                "Erro ao carregar produtos:",
                erro
            );

            campoProduto.replaceChildren();

            const opcaoErro = document.createElement("option");

            opcaoErro.value = "";
            opcaoErro.textContent =
                "Erro ao carregar produtos";

            campoProduto.appendChild(opcaoErro);

            alert(
                "Nao foi possivel carregar os produtos. " +
                "Verifique se o backend Java esta funcionando."
            );
        }
    }

    // Calcula automaticamente o custo total de aquisicao
    function calcularCustoTotal() {

        const quantidade = Number(
            campoQuantidade.value
        );

        const valorUnitario = Number(
            campoValorUnitario.value
        );

        if (
            campoQuantidade.value === "" ||
            campoValorUnitario.value === "" ||
            !Number.isFinite(quantidade) ||
            !Number.isFinite(valorUnitario) ||
            quantidade <= 0 ||
            valorUnitario <= 0
        ) {

            campoCustoTotal.value = "";
            return;
        }

        const custoTotal =
            quantidade * valorUnitario;

        campoCustoTotal.value =
            custoTotal.toFixed(2);
    }

    // Atualiza o custo total quando a quantidade mudar
    campoQuantidade.addEventListener(
        "input",
        calcularCustoTotal
    );

    // Atualiza o custo total quando o valor mudar
    campoValorUnitario.addEventListener(
        "input",
        calcularCustoTotal
    );

    // Limpa o custo total quando o formulario for redefinido
    formulario.addEventListener("reset", () => {
        campoCustoTotal.value = "";
    });

    // Cadastra um novo lote
    formulario.addEventListener("submit", async (evento) => {

        evento.preventDefault();

        // Verifica se os campos obrigatorios foram preenchidos
        if (!formulario.reportValidity()) {
            return;
        }

        // Recupera os dados preenchidos
        const lote = {

            itemId:
                campoProduto.value,

            codigo:
                document.getElementById("codigoLote")
                    .value.trim(),

            quantidadeRecebida:
                campoQuantidade.value,

            dataEntrada:
                document.getElementById("dataEntrada")
                    .value,

            dataValidade:
                document.getElementById("dataValidade")
                    .value,

            valorUnitario:
                campoValorUnitario.value,

            observacao:
                document.getElementById("observacao")
                    .value.trim()
        };

        // Verifica se um produto foi selecionado
        if (!lote.itemId) {

            alert("Selecione um produto.");
            return;
        }

        // Verifica se a validade e anterior a entrada
        if (lote.dataValidade < lote.dataEntrada) {

            alert(
                "A validade nao pode ser anterior a data de entrada."
            );
            return;
        }

        const botao = formulario.querySelector(
            'button[type="submit"]'
        );

        // Desativa o botao durante o cadastro
        botao.disabled = true;
        botao.textContent = "Cadastrando...";

        try {

            console.log(
                "Enviando lote ao backend:",
                lote
            );

            // Envia os dados para o Java
            const resposta = await fetch(
                `${API}/lotes`,
                {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                        "Accept": "application/json"
                    },
                    body: JSON.stringify(lote)
                }
            );

            // Le a resposta enviada pelo backend
            const textoResposta = await resposta.text();

            let resultado;

            try {

                resultado = JSON.parse(textoResposta);

            } catch (erro) {

                throw new Error(
                    "O backend nao retornou um JSON valido."
                );
            }

            // Verifica se ocorreu algum erro HTTP
            if (!resposta.ok) {

                throw new Error(
                    resultado.mensagem ||
                    `Erro HTTP ${resposta.status} ao cadastrar lote.`
                );
            }

            // Confirma que o lote foi cadastrado
            alert(
                resultado.mensagem ||
                "Lote cadastrado com sucesso!"
            );

            console.log(
                "Lote cadastrado:",
                resultado
            );

            // Limpa os campos do formulario
            formulario.reset();
            campoCustoTotal.value = "";

        } catch (erro) {

            console.error(
                "Erro ao cadastrar lote:",
                erro
            );

            // Diferencia erros de conexao dos erros do backend
            if (erro instanceof TypeError) {

                alert(
                    "Falha na comunicacao com o backend. " +
                    "Verifique se o servidor Java esta ativo " +
                    "e se a rota /api/lotes esta funcionando."
                );

            } else {

                alert(
                    erro.message ||
                    "Nao foi possivel cadastrar o lote."
                );
            }

        } finally {

            // Reativa o botao apos a tentativa
            botao.disabled = false;
            botao.textContent = "Cadastrar novo lote";
        }
    });

    // Busca os produtos quando a pagina abrir
    carregarProdutos();

});
