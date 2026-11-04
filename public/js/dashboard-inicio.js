const options = {
  chart: {
    type: "area",
    height: 280,
    toolbar: {
      show: false,
    },
  },

  // Vou usar os dados do Banco Aqui
  series: [
    {
      name: "Produtos Perdidos",
      data: [],
    },
  ],

  xaxis: {
    categories: [],
  },

  colors: ["#1F6F78"],

  stroke: {
    curve: "smooth",
    width: 3,
  },

  dataLabels: {
    enabled: false,
  },
};

const API_URL = "http://localhost:8080";

async function carregarIndicadores() {
  try {
    // Busca os indicadores do Dashboard
    const respostaDashboard = await fetch(`${API_URL}/api/dashboard/resumo`);

    if (!respostaDashboard.ok) {
      throw new Error("Erro ao consultar Dashboard");
    }

    const dadosDashboard = await respostaDashboard.json();

    // Busca os indicadores de desperdício
    const respostaRelatorios = await fetch(
      `${API_URL}/api/relatorios/indicadores`,
    );

    if (!respostaRelatorios.ok) {
      throw new Error("Erro ao consultar Relatórios");
    }

    const dadosRelatorios = await respostaRelatorios.json();

    // Atualiza os cards HTML
    document.querySelector("#total-produtos").textContent =
      dadosDashboard.totalProdutos;

    document.querySelector("#lotes-proximos").textContent =
      dadosDashboard.lotesProximosVencimento;

    document.querySelector("#total-desperdicios").textContent =
      dadosRelatorios.desperdicados;
  } catch (erro) {
    console.error("Erro ao carregar indicadores:", erro);
  }
}

async function carregarProximosVencimentos() {
  const tabela = document.querySelector("#tabela-proximos-vencimentos");

  try {
    const resposta = await fetch(
      `${API_URL}/api/lotes/proximos-vencimento?dias=7`,
    );

    if (!resposta.ok) {
      throw new Error("Erro ao consultar próximos vencimentos");
    }

    const dados = await resposta.json();

    // Remove os dados anteriores da tabela
    tabela.replaceChildren();

    // Verifica se não existem lotes próximos do vencimento
    if (dados.lotes.length === 0) {
      const linha = document.createElement("tr");
      const coluna = document.createElement("td");

      coluna.colSpan = 3;
      coluna.textContent = "Nenhum lote próximo do vencimento.";

      linha.appendChild(coluna);
      tabela.appendChild(linha);
      return;
    }

    for (const lote of dados.lotes) {
      const linha = document.createElement("tr");

      const colunaProduto = document.createElement("td");
      colunaProduto.textContent = lote.produtoNome;

      const colunaQuantidade = document.createElement("td");
      colunaQuantidade.textContent = `${lote.quantidadeDisponivel} ${lote.unidadeMedida}`;

      const colunaValidade = document.createElement("td");

      const diasRestantes = Math.round(
        (Date.parse(lote.dataValidade) - Date.parse(dados.dataInicio)) /
          86400000,
      );

      if (diasRestantes === 0) {
        colunaValidade.textContent = "Vence hoje";
      } else if (diasRestantes === 1) {
        colunaValidade.textContent = "Vence amanhã";
      } else {
        colunaValidade.textContent = `Vence em ${diasRestantes} dias`;
      }

      if (diasRestantes <= 1) {
        colunaValidade.classList.add("text-danger");
      }

      linha.append(colunaProduto, colunaQuantidade, colunaValidade);

      tabela.appendChild(linha);
    }
  } catch (erro) {
    console.error("Erro ao carregar vencimentos:", erro);
    tabela.innerHTML =
      '<tr><td colspan="3">Erro ao carregar vencimentos.</td></tr>';
  }
}

async function carregarEvolucaoMensal() {
  try {
    const resposta = await fetch(
      `${API_URL}/api/relatorios/desperdicios/evolucao-mensal`,
    );

    if (!resposta.ok) {
      throw new Error("Erro ao consultar evolução mensal");
    }

    const dados = await resposta.json();

    const nomesMes = [
      "Jan",
      "Fev",
      "Mar",
      "Abr",
      "Mai",
      "Jun",
      "Jul",
      "Ago",
      "Set",
      "Out",
      "Nov",
      "Dez",
    ];

    const meses = dados.evolucaoMensal.map((registro) => {
      const [ano, mes] = registro.mes.split("-");

      return `${nomesMes[Number(mes) - 1]}/${ano.slice(-2)}`;
    });

    const ocorrencias = dados.evolucaoMensal.map(
      (registro) => registro.totalOcorrencias,
    );

    grafico.updateOptions({
      xaxis: {
        categories: meses,
      },
    });

    grafico.updateSeries([
      {
        name: "Ocorrências de desperdício",
        data: ocorrencias,
      },
    ]);
  } catch (error) {
    console.log("Erro ao carregar evolução mensal:", error);
  }
}

const elemento = document.querySelector("#area-chart");
const grafico = new ApexCharts(elemento, options);

carregarIndicadores();
carregarProximosVencimentos();
grafico.render().then(() => {
  carregarEvolucaoMensal();
});
