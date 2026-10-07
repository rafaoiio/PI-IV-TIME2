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
      data: [30, 25, 18, 22, 15, 10],
    },
  ],

  xaxis: {
    categories: ["Abr", "Mai", "Jun", "Jul", "Ago", "Set"],
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

const elemento = document.querySelector("#area-chart");
const grafico = new ApexCharts(elemento, options);

grafico.render();
