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

        
        // Cria os botoes de acoes
        const celulaAcoes = document.createElement("td");

        celulaAcoes.className =
            "rounded-r-md px-3 py-3";

        const containerAcoes = document.createElement("div");

        containerAcoes.className =
            "flex items-center justify-center gap-3";

        // Botao de editar lote
        const editar = document.createElement("button");

        editar.type = "button";
        editar.disabled = true;
        editar.title = "Edicao ainda nao disponivel";
        editar.setAttribute("aria-label", "Editar lote");

        editar.className =
            "cursor-not-allowed text-primary-dark opacity-50";

        editar.innerHTML = `
            <svg
                xmlns="http://www.w3.org/2000/svg"
                width="22"
                height="22"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                stroke-width="1.8"
                aria-hidden="true"
            >
                <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    d="M12 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Z"
                />
                <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-1.6 1.6-.06-.06A1.7 1.7 0 0 0 16.26 18a1.7 1.7 0 0 0-1.26 1.55V20h-2v-.45a1.7 1.7 0 0 0-1.26-1.55 1.7 1.7 0 0 0-1.88.34l-.06.06-1.6-1.6.06-.06A1.7 1.7 0 0 0 8.6 15a1.7 1.7 0 0 0-1.55-1.26H6.6v-2h.45A1.7 1.7 0 0 0 8.6 10.5a1.7 1.7 0 0 0-.34-1.88L8.2 8.56l1.6-1.6.06.06A1.7 1.7 0 0 0 11.74 7 1.7 1.7 0 0 0 13 5.45V5h2v.45A1.7 1.7 0 0 0 16.26 7a1.7 1.7 0 0 0 1.88-.34l.06-.06 1.6 1.6-.06.06A1.7 1.7 0 0 0 19.4 10.5a1.7 1.7 0 0 0 1.55 1.26h.45v2h-.45A1.7 1.7 0 0 0 19.4 15Z"
                />
            </svg>
        `;

        // Botao de excluir lote
        const excluir = document.createElement("button");

        excluir.type = "button";
        excluir.disabled = true;
        excluir.title = "Exclusao ainda nao disponivel";
        excluir.setAttribute("aria-label", "Excluir lote");

        excluir.className =
            "cursor-not-allowed text-primary-dark opacity-50";

        excluir.innerHTML = `
            <svg
                xmlns="http://www.w3.org/2000/svg"
                width="22"
                height="22"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                stroke-width="1.8"
                aria-hidden="true"
            >
                <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    d="M3 6h18m-2 0-1 14H6L5 6m3 0V4h8v2M10 10v7m4-7v7"
                />
            </svg>
        `;

        // Adiciona os botoes na tabela
        containerAcoes.appendChild(editar);
        containerAcoes.appendChild(excluir);

        celulaAcoes.appendChild(containerAcoes);

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
