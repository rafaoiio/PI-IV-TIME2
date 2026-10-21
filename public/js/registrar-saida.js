
document.addEventListener("DOMContentLoaded", () => {
    const API = "http://localhost:8080";

    // Elementos do formulario
    const formulario = document.getElementById("form-registrar-saida");
    const produtoSelect = document.getElementById("produto");
    const loteSelect = document.getElementById("lote");
    const quantidadeDisponivel = document.getElementById("quantidade-disponivel");
    const quantidadeSaida = document.getElementById("quantidade-saida");
    const dataSaida = document.getElementById("data-saida");
    const motivo = document.getElementById("motivo");
    const observacao = document.getElementById("observacao");

    const botaoEnviar = formulario.querySelector('[type="submit"]');
    const botaoCancelar = formulario.querySelector('button[type="button"]');

    let produtos = [];
    let lotes = [];

    // Guarda os dados de uma operacao ainda nao confirmada.
    // A mesma operacao sempre reutiliza a mesma chave.
    let operacaoPendente = null;
    let enviando = false;

    // Retorna a data atual no horario de Sao Paulo
    function dataAtual() {
        const partes = new Intl.DateTimeFormat("en-CA", {
            timeZone: "America/Sao_Paulo",
            year: "numeric",
            month: "2-digit",
            day: "2-digit"
        }).formatToParts(new Date());

        const valores = Object.fromEntries(
            partes.map(parte => [parte.type, parte.value])
        );

        return `${valores.year}-${valores.month}-${valores.day}`;
    }

    dataSaida.value = dataAtual();

    // Cria uma opcao para os campos select
    function criarOpcao(valor, texto) {
        const opcao = document.createElement("option");

        opcao.value = valor;
        opcao.textContent = texto;

        return opcao;
    }

    // Limpa a selecao de lote e as quantidades
    function limparLotes() {
        loteSelect.replaceChildren(
            criarOpcao("", "Selecione um lote disponível")
        );

        loteSelect.value = "";
        quantidadeDisponivel.value = "";
        quantidadeSaida.value = "";
    }

    // Localiza o lote selecionado
    function loteSelecionado() {
        return lotes.find(
            lote => lote.id === loteSelect.value
        );
    }

    // Bloqueia o formulario durante o envio
    // e quando existe uma operacao com resultado incerto.
    function atualizarBloqueio() {
        const travarCampos =
            enviando || operacaoPendente !== null;

        formulario.querySelectorAll(
            "input, select, textarea"
        ).forEach(campo => {
            // Campo de saldo sempre e somente leitura.
            if (campo === quantidadeDisponivel) {
                return;
            }

            campo.disabled = travarCampos;
        });

        if (!travarCampos) {
            produtoSelect.disabled = produtos.length === 0;

            const produto = produtos.find(
                item => item.id === produtoSelect.value
            );

            const existemLotes = produto && lotes.some(lote => {
                const mesmoProduto = lote.itemId
                    ? lote.itemId === produto.id
                    : lote.produto === produto.nome;

                return mesmoProduto &&
                    Number(lote.quantidadeDisponivel) > 0;
            });

            loteSelect.disabled = !existemLotes;
        }

        // O botao de envio permanece disponivel para
        // repetir uma operacao pendente com a mesma chave.
        botaoEnviar.disabled = enviando;

        // Nao permite cancelar uma operacao incerta.
        if (botaoCancelar) {
            botaoCancelar.disabled = travarCampos;
        }
    }

    // Busca produtos e lotes no backend
    async function carregarDados() {
        produtoSelect.disabled = true;
        loteSelect.disabled = true;

        try {
            const [respostaProdutos, respostaLotes] =
                await Promise.all([
                    fetch(`${API}/api/produtos`, {
                        cache: "no-store"
                    }),
                    fetch(`${API}/api/lotes`, {
                        cache: "no-store"
                    })
                ]);

            if (!respostaProdutos.ok || !respostaLotes.ok) {
                throw new Error(
                    "Erro ao consultar produtos e lotes."
                );
            }

            const dadosProdutos = await respostaProdutos.json();
            const dadosLotes = await respostaLotes.json();

            if (
                !Array.isArray(dadosProdutos.produtos) ||
                !Array.isArray(dadosLotes.lotes)
            ) {
                throw new Error(
                    "Formato de resposta invalido."
                );
            }

            produtos = dadosProdutos.produtos.filter(
                produto => produto.status === "ativo"
            );

            lotes = dadosLotes.lotes;

            produtoSelect.replaceChildren(
                criarOpcao("", "Selecione um produto cadastrado")
            );

            produtos.forEach(produto => {
                produtoSelect.appendChild(
                    criarOpcao(produto.id, produto.nome)
                );
            });

            limparLotes();

            return true;

        } catch (erro) {
            console.error(
                "Erro ao carregar os dados:",
                erro
            );

            alert(
                "Não foi possível carregar os produtos e lotes. " +
                "Verifique se o backend Java está funcionando."
            );

            return false;

        } finally {
            atualizarBloqueio();
        }
    }

    // Mostra os lotes do produto selecionado
    produtoSelect.addEventListener("change", () => {
        if (operacaoPendente || enviando) {
            return;
        }

        limparLotes();

        const produto = produtos.find(
            item => item.id === produtoSelect.value
        );

        if (!produto) {
            loteSelect.disabled = true;
            return;
        }

        // Usa itemId quando disponivel.
        // Mantem compatibilidade com a API atual,
        // que pode retornar somente o nome do produto.
        const lotesDisponiveis = lotes.filter(lote => {
            const mesmoProduto = lote.itemId
                ? lote.itemId === produto.id
                : lote.produto === produto.nome;

            return mesmoProduto &&
                Number(lote.quantidadeDisponivel) > 0;
        });

        lotesDisponiveis.forEach(lote => {
            const texto =
                `${lote.codigo} — Saldo: ` +
                `${lote.quantidadeDisponivel} ` +
                `${produto.unidadeMedida || ""}`;

            loteSelect.appendChild(
                criarOpcao(lote.id, texto)
            );
        });

        if (lotesDisponiveis.length === 0) {
            loteSelect.replaceChildren(
                criarOpcao("", "Nenhum lote disponível")
            );
        }

        loteSelect.disabled = lotesDisponiveis.length === 0;
    });

    // Preenche automaticamente a quantidade disponivel
    loteSelect.addEventListener("change", () => {
        if (operacaoPendente || enviando) {
            return;
        }

        const lote = loteSelecionado();

        quantidadeDisponivel.value = lote
            ? lote.quantidadeDisponivel
            : "";

        quantidadeSaida.value = "";
    });

    // Cancela apenas um formulario sem operacao pendente
    if (botaoCancelar) {
        botaoCancelar.addEventListener("click", () => {
            if (enviando || operacaoPendente) {
                return;
            }

            formulario.reset();

            limparLotes();

            dataSaida.value = dataAtual();

            atualizarBloqueio();
        });
    }

    // Valida o formulario e prepara uma nova operacao
    function prepararOperacao() {
        const produto = produtos.find(
            item => item.id === produtoSelect.value
        );

        const lote = loteSelecionado();

        if (!produto || !lote) {
            throw new Error(
                "Selecione um produto e um lote válido."
            );
        }

        const quantidadeTexto = quantidadeSaida.value
            .trim()
            .replace(",", ".");

        const quantidade = Number(quantidadeTexto);
        const saldo = Number(lote.quantidadeDisponivel);

        if (
            !quantidadeTexto ||
            !Number.isFinite(quantidade) ||
            quantidade <= 0
        ) {
            throw new Error(
                "Informe uma quantidade de saída válida."
            );
        }

        if (quantidade > saldo) {
            throw new Error(
                "A quantidade de saída não pode superar " +
                "o saldo disponível."
            );
        }

        if (
            !dataSaida.value ||
            !motivo.value ||
            !observacao.value.trim()
        ) {
            throw new Error(
                "Preencha todos os campos obrigatórios."
            );
        }

        if (observacao.value.trim().length > 1000) {
            throw new Error(
                "A observação deve ter no máximo 1000 caracteres."
            );
        }

        if (!globalThis.crypto?.randomUUID) {
            throw new Error(
                "O navegador não conseguiu gerar " +
                "o identificador da operação."
            );
        }

        // Os dados sao preparados somente uma vez.
        // Em um reenvio, esta funcao nao sera executada.
        return {
            itemId: produto.id,
            loteId: lote.id,
            quantidade: quantidadeTexto,
            dataSaida: dataSaida.value,
            motivo: motivo.value,
            observacao: observacao.value.trim(),
            idempotencyKey: crypto.randomUUID()
        };
    }

    // Registra a saida no backend
    formulario.addEventListener("submit", async evento => {
        evento.preventDefault();

        // Impede dois envios simultaneos
        if (enviando) {
            return;
        }

        // Se uma operacao anterior ainda nao foi confirmada,
        // reutiliza exatamente os mesmos dados.
        if (!operacaoPendente) {
            try {
                const dados = prepararOperacao();

                operacaoPendente = {
                    dados: dados
                };

            } catch (erro) {
                alert(erro.message);
                return;
            }
        }

        enviando = true;
        atualizarBloqueio();

        try {
            const resposta = await fetch(
                `${API}/api/movimentacoes`,
                {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify(
                        operacaoPendente.dados
                    )
                }
            );

            let resultado = {};

            try {
                resultado = await resposta.json();
            } catch (erroLeitura) {
                // Resposta sem JSON valido.
            }

            if (!resposta.ok) {
                throw new Error(
                    resultado.mensagem ||
                    resultado.erro ||
                    "Não foi possível confirmar a saída."
                );
            }

            // O backend confirmou a operacao.
            // Somente agora a chave pode ser descartada.
            operacaoPendente = null;

            formulario.reset();
            dataSaida.value = dataAtual();

            alert(
                resultado.mensagem ||
                "Saída registrada com sucesso!"
            );

        } catch (erro) {
            console.error(
                "Erro ao registrar saída:",
                erro
            );

            // Mantem os dados e a chave da operacao.
            // Um novo clique repetira a mesma requisicao.
            alert(
                "Não foi possível confirmar a saída.\n\n" +
                "Os dados foram preservados para evitar " +
                "um registro duplicado.\n\n" +
                "Você pode tentar novamente usando " +
                "o botão Registrar Saída, sem recarregar " +
                "a página.\n\n" +
                "Detalhes: " + erro.message
            );

        } finally {
            enviando = false;
            atualizarBloqueio();
        }

        // Atualiza o saldo somente quando a operacao
        // foi confirmada e nao existe outra pendente.
        if (!operacaoPendente) {
            await carregarDados();
        }
    });

    // Carregamento inicial
    carregarDados();
});
