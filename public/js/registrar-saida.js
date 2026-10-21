
document.addEventListener("DOMContentLoaded", () => {
    const API = "http://localhost:8080";

    const formulario = document.getElementById("form-registrar-saida");
    const produtoSelect = document.getElementById("produto");
    const loteSelect = document.getElementById("lote");
    const quantidadeDisponivel = document.getElementById("quantidade-disponivel");
    const quantidadeSaida = document.getElementById("quantidade-saida");
    const dataSaida = document.getElementById("data-saida");
    const motivo = document.getElementById("motivo");
    const observacao = document.getElementById("observacao");
    const botaoEnviar = formulario.querySelector('[type="submit"]');

    let produtos = [];
    let lotes = [];

    // Operacao pendente e sua identificacao unica
    let operacaoPendente = null;
    let enviando = false;

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

    function criarOpcao(valor, texto) {
        const opcao = document.createElement("option");
        opcao.value = valor;
        opcao.textContent = texto;
        return opcao;
    }

    function limparLotes() {
        loteSelect.replaceChildren(
            criarOpcao("", "Selecione um lote disponível")
        );

        loteSelect.value = "";
        quantidadeDisponivel.value = "";
        quantidadeSaida.value = "";
    }

    function loteSelecionado() {
        return lotes.find(lote => lote.id === loteSelect.value);
    }

    // Invalida a operacao anterior quando o formulario e alterado
    function invalidarOperacao() {
        if (!enviando) {
            operacaoPendente = null;
        }
    }

    formulario.addEventListener("input", invalidarOperacao);
    formulario.addEventListener("change", invalidarOperacao);

    // Impede alteracoes enquanto uma requisicao esta em andamento
    function bloquearFormulario(bloquear) {
        enviando = bloquear;

        formulario.querySelectorAll(
            "input, select, textarea, button"
        ).forEach(campo => {
            if (campo === quantidadeDisponivel) {
                return;
            }

            campo.disabled = bloquear;
        });

        botaoEnviar.disabled = bloquear;
    }

    // Busca produtos e lotes no backend
    async function carregarDados() {
        produtoSelect.disabled = true;
        loteSelect.disabled = true;

        try {
            const [respostaProdutos, respostaLotes] = await Promise.all([
                fetch(`${API}/api/produtos`, {
                    cache: "no-store"
                }),
                fetch(`${API}/api/lotes`, {
                    cache: "no-store"
                })
            ]);

            if (!respostaProdutos.ok || !respostaLotes.ok) {
                throw new Error("Erro ao consultar as APIs.");
            }

            const dadosProdutos = await respostaProdutos.json();
            const dadosLotes = await respostaLotes.json();

            if (!Array.isArray(dadosProdutos.produtos) ||
                !Array.isArray(dadosLotes.lotes)) {
                throw new Error("Formato de resposta inválido.");
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

        } catch (erro) {
            console.error("Erro ao carregar dados:", erro);

            alert(
                "Não foi possível carregar os produtos e lotes. " +
                "Verifique se o backend Java está em execução."
            );

        } finally {
            produtoSelect.disabled = false;
        }
    }

    // Mostra somente lotes com saldo do produto selecionado
    produtoSelect.addEventListener("change", () => {
        limparLotes();

        const produto = produtos.find(
            item => item.id === produtoSelect.value
        );

        if (!produto) {
            loteSelect.disabled = true;
            return;
        }

        // Associacao provisoria pelo nome retornado pela API
        // O backend valida a relacao real entre itemId e loteId.
        const lotesDisponiveis = lotes.filter(lote =>
            lote.produto === produto.nome &&
            Number(lote.quantidadeDisponivel) > 0
        );

        lotesDisponiveis.forEach(lote => {
            const texto =
                `${lote.codigo} — Saldo: ${lote.quantidadeDisponivel} ` +
                `${produto.unidadeMedida || ""}`;

            loteSelect.appendChild(
                criarOpcao(lote.id, texto)
            );
        });

        loteSelect.disabled = lotesDisponiveis.length === 0;

        if (lotesDisponiveis.length === 0) {
            loteSelect.replaceChildren(
                criarOpcao("", "Nenhum lote disponível")
            );
        }
    });

    // Preenche automaticamente o saldo do lote
    loteSelect.addEventListener("change", () => {
        const lote = loteSelecionado();

        quantidadeDisponivel.value = lote
            ? lote.quantidadeDisponivel
            : "";

        quantidadeSaida.value = "";
    });

    // Cancela o preenchimento sem registrar movimentacao
    const botaoCancelar = formulario.querySelector(
        'button[type="button"]'
    );

    if (botaoCancelar) {
        botaoCancelar.addEventListener("click", () => {
            if (enviando) {
                return;
            }

            operacaoPendente = null;
            formulario.reset();
            limparLotes();
            loteSelect.disabled = true;
            dataSaida.value = dataAtual();
        });
    }

    // Registro de saida
    formulario.addEventListener("submit", async evento => {
        evento.preventDefault();

        if (enviando) {
            return;
        }

        const produto = produtos.find(
            item => item.id === produtoSelect.value
        );

        const lote = loteSelecionado();

        if (!produto || !lote) {
            alert("Selecione um produto e um lote válido.");
            return;
        }

        const quantidadeTexto = quantidadeSaida.value
            .trim()
            .replace(",", ".");

        const quantidade = Number(quantidadeTexto);
        const saldo = Number(lote.quantidadeDisponivel);

        if (!quantidadeTexto ||
            !Number.isFinite(quantidade) ||
            quantidade <= 0) {

            alert("Informe uma quantidade de saída válida.");
            return;
        }

        if (quantidade > saldo) {
            alert(
                "A quantidade de saída não pode superar " +
                "o saldo disponível."
            );
            return;
        }

        if (!dataSaida.value ||
            !motivo.value ||
            !observacao.value.trim()) {

            alert("Preencha todos os campos obrigatórios.");
            return;
        }

        // Dados da operacao
        const dados = {
            itemId: produto.id,
            loteId: lote.id,
            quantidade: quantidadeTexto,
            dataSaida: dataSaida.value,
            motivo: motivo.value,
            observacao: observacao.value.trim()
        };

        // Representacao do conteudo da solicitacao
        const assinatura = JSON.stringify(dados);

        // Nova operacao: gera uma chave.
        // Reenvio dos mesmos dados: preserva a chave anterior.
        if (!operacaoPendente ||
            operacaoPendente.assinatura !== assinatura) {

            if (!globalThis.crypto?.randomUUID) {
                alert(
                    "Não foi possível gerar o identificador " +
                    "da operação. Verifique o ambiente."
                );
                return;
            }

            operacaoPendente = {
                chave: crypto.randomUUID(),
                assinatura
            };
        }

        dados.idempotencyKey = operacaoPendente.chave;

        bloquearFormulario(true);

        try {
            const resposta = await fetch(
                `${API}/api/movimentacoes`,
                {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify(dados)
                }
            );

            const resultado = await resposta.json();

            if (!resposta.ok) {
                throw new Error(
                    resultado.mensagem ||
                    resultado.erro ||
                    "Não foi possível registrar a saída."
                );
            }

            // Somente uma resposta de sucesso confirma a operacao
            operacaoPendente = null;

            alert(
                resultado.mensagem ||
                "Saída registrada com sucesso!"
            );

            formulario.reset();

            await carregarDados();

            dataSaida.value = dataAtual();
            loteSelect.disabled = true;

        } catch (erro) {
            console.error(
                "Erro ao registrar saída:",
                erro
            );

            alert(
                erro.message ||
                "Não foi possível confirmar o registro. " +
                "Tente novamente sem alterar os dados."
            );

            // Mantem a chave para eventual reenvio dos mesmos dados

        } finally {
            bloquearFormulario(false);
        }
    });

    carregarDados();
});
