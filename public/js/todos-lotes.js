document.addEventListener("DOMContentLoaded", () => {

    const tabelaLotes =
        document.getElementById("tabelaLotes");

    const API = "http://localhost:8080/api";

    // Formata os valores em reais
    function formatarMoeda(valor) {

        const numero = Number(valor);

        if (!Number.isFinite(numero)) {
            return "-";
        }

        return numero.toLocaleString("pt-BR", {
            style: "currency",
            currency: "BRL"
        });
    }

    // Formata as quantidades
    function formatarQuantidade(valor) {

        const numero = Number(valor);

        if (!Number.isFinite(numero)) {
            return "-";
        }

        return numero.toLocaleString("pt-BR", {
            maximumFractionDigits: 3
        });
    }

    // Formata datas sem alterar o dia pelo fuso horario
    function formatarData(data) {

        if (!data) {
            return "-";
        }

        const texto = String(data);
        const partes = texto.slice(0, 10).split("-");

        if (partes.length !== 3) {
            return "-";
        }

        return `${partes[2]}/${partes[1]}/${partes[0]}`;
    }

    // Calcula o status a partir da validade e da quantidade
    function calcularStatus(lote) {

        const quantidade = Number(lote.quantidadeDisponivel);

        if (quantidade <= 0) {
            return "Esgotado";
        }

        const validade = String(lote.dataValidade).slice(0, 10);

        const hoje = new Date();

        const dataHoje = [
            hoje.getFullYear(),
            String(hoje.getMonth() + 1).padStart(2, "0"),
            String(hoje.getDate()).padStart(2, "0")
        ].join("-");

        if (validade < dataHoje) {
            return "Vencido";
        }

        const dataValidade = new Date(`${validade}T00:00:00`);
        const dataAtual = new Date(`${dataHoje}T00:00:00`);

        const diferencaDias = Math.round(
            (dataValidade - dataAtual) / 86400000
        );

        if (diferencaDias <= 30) {
            return "Próx. do vencimento";
        }

        return "Ativo";
    }

    // Cria uma celula da tabela
    function criarCelula(texto, classes = "") {

        const celula = document.createElement("td");

        celula.className =
            `px-3 py-3 font-semibold ${classes}`;

        celula.textContent = texto;

        return celula;
    }

    // Cria uma linha com os dados de um lote
    function criarLinha(lote) {

        const linha = document.createElement("tr");

        linha.className =
            "border-b border-gray-300 bg-[#e9ebee]";

        const status = calcularStatus(lote);

        linha.appendChild(
            criarCelula(lote.codigo || "-", "rounded-l-md")
        );

        linha.appendChild(
            criarCelula(lote.produto || "Produto não encontrado")
        );

        linha.appendChild(
            criarCelula(formatarQuantidade(lote.quantidadeInicial))
        );

        linha.appendChild(
            criarCelula(formatarQuantidade(lote.quantidadeDisponivel))
        );

        linha.appendChild(
            criarCelula(formatarMoeda(lote.custoTotalAquisicao))
        );

        linha.appendChild(
            criarCelula(formatarData(lote.dataEntrada))
        );

        linha.appendChild(
            criarCelula(formatarData(lote.dataValidade))
        );

        linha.appendChild(
            criarCelula(status, "text-center")
        );

        // Acoes aguardam a integracao de editar e excluir
        const celulaAcoes = document.createElement("td");

        celulaAcoes.className =
            "rounded-r-md px-3 py-3 text-center";

        const editar = document.createElement("button");

        editar.type = "button";
        editar.disabled = true;
        editar.title = "Edição ainda não disponível";
        editar.setAttribute("aria-label", "Editar lote");
        editar.className =
            "mr-3 cursor-not-allowed text-gray-400";

        editar.textContent = "Editar";

        const excluir = document.createElement("button");

        excluir.type = "button";
        excluir.disabled = true;
        excluir.title = "Exclusão ainda não disponível";
        excluir.setAttribute("aria-label", "Excluir lote");
        excluir.className =
            "cursor-not-allowed text-gray-400";

        excluir.textContent = "Excluir";

        celulaAcoes.appendChild(editar);
        celulaAcoes.appendChild(excluir);

        linha.appendChild(celulaAcoes);

        return linha;
    }

    // Mostra mensagens na tabela
    function mostrarMensagem(mensagem) {

        tabelaLotes.replaceChildren();

        const linha = document.createElement("tr");
        const celula = document.createElement("td");

        celula.colSpan = 9;

        celula.className =
            "px-3 py-6 text-center text-text-secondary";

        celula.textContent = mensagem;

        linha.appendChild(celula);
        tabelaLotes.appendChild(linha);
    }

    // Busca os lotes no backend Java
    async function carregarLotes() {

        mostrarMensagem("Carregando lotes...");

        try {

            const resposta = await fetch(
                `${API}/lotes`,
                {
                    method: "GET",
                    headers: {
                        "Accept": "application/json"
                    }
                }
            );

            const resultado = await resposta.json();

            if (!resposta.ok) {

                throw new Error(
                    resultado.mensagem ||
                    "Erro ao buscar lotes."
                );
            }

            if (!Array.isArray(resultado.lotes)) {

                throw new Error(
                    "Resposta inválida do servidor."
                );
            }

            if (resultado.lotes.length === 0) {

                mostrarMensagem(
                    "Nenhum lote cadastrado."
                );

                return;
            }

            tabelaLotes.replaceChildren();

            resultado.lotes.forEach((lote) => {

                tabelaLotes.appendChild(
                    criarLinha(lote)
                );

            });

        } catch (erro) {

            console.error(
                "Erro ao carregar lotes:",
                erro
            );

            mostrarMensagem(
                "Não foi possível carregar os lotes."
            );
        }
    }

    // Carrega os lotes quando a pagina abrir
    carregarLotes();

});
