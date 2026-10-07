const formCadastro = document.getElementById("formCadastro");

const nomeInput = document.getElementById("nome");
const emailInput = document.getElementById("email");
const celularInput = document.getElementById("celular");

const erroNome = document.getElementById("erroNome");
const erroEmail = document.getElementById("erroEmail");
const erroCelular = document.getElementById("erroCelular");


/* =========================================
   FUNÇÕES DE ERRO
========================================= */

function mostrarErro(input, elementoErro, mensagem) {

  elementoErro.textContent = mensagem;
  elementoErro.classList.remove("hidden");

  input.classList.remove("border-border");
  input.classList.add("border-danger");

  input.setAttribute("aria-invalid", "true");
}


function removerErro(input, elementoErro) {

  elementoErro.textContent = "";
  elementoErro.classList.add("hidden");

  input.classList.remove("border-danger");
  input.classList.add("border-border");

  input.removeAttribute("aria-invalid");
}


/* =========================================
   VALIDAÇÃO DO NOME
========================================= */

function validarNome() {

  const nome = nomeInput.value.trim();

  const partes = nome
    .split(/\s+/)
    .filter(parte => parte.length > 0);


  if (nome === "") {

    mostrarErro(
      nomeInput,
      erroNome,
      "Informe seu nome completo."
    );

    return false;

  }


  if (nome.length < 3) {

    mostrarErro(
      nomeInput,
      erroNome,
      "Digite um nome válido."
    );

    return false;

  }


  if (partes.length < 2) {

    mostrarErro(
      nomeInput,
      erroNome,
      "Digite seu nome e sobrenome."
    );

    return false;

  }


  removerErro(nomeInput, erroNome);

  return true;

}


/* =========================================
   VALIDAÇÃO DO E-MAIL
========================================= */

function validarEmail() {

  const email = emailInput.value.trim();


  if (email === "") {

    mostrarErro(
      emailInput,
      erroEmail,
      "Informe seu e-mail."
    );

    return false;

  }


  /*
    Validação simples.
    Exemplo esperado:

    usuario@email.com
  */

  const emailRegex =
    /^[^\s@]+@[^\s@]+\.[^\s@]+$/;


  if (!emailRegex.test(email)) {

    mostrarErro(
      emailInput,
      erroEmail,
      "Digite um e-mail válido."
    );

    return false;

  }


  removerErro(emailInput, erroEmail);

  return true;

}


/* =========================================
   MÁSCARA DO CELULAR
========================================= */

celularInput.addEventListener("input", function () {

  let numeros =
    celularInput.value.replace(/\D/g, "");


  /*
    Caso o valor já esteja começando por 55,
    removemos antes de formatar.
  */

  if (numeros.startsWith("55")) {
    numeros = numeros.substring(2);
  }


  // DDD + número celular
  numeros = numeros.substring(0, 11);


  let valor = "+55";


  if (numeros.length > 0) {

    valor +=
      " (" +
      numeros.substring(0, 2);

  }


  if (numeros.length >= 2) {

    valor += ")";

  }


  if (numeros.length > 2) {

    valor +=
      " " +
      numeros.substring(2, 7);

  }


  if (numeros.length > 7) {

    valor +=
      "-" +
      numeros.substring(7, 11);

  }


  celularInput.value = valor;


  /*
    Se o usuário já havia recebido erro,
    validamos novamente enquanto ele digita.
  */

  if (!erroCelular.classList.contains("hidden")) {
    validarCelular();
  }

});


/* =========================================
   VALIDAÇÃO DO CELULAR
========================================= */

function validarCelular() {

  const numeros =
    celularInput.value.replace(/\D/g, "");


  if (
    celularInput.value === "" ||
    celularInput.value === "+55"
  ) {

    mostrarErro(
      celularInput,
      erroCelular,
      "Informe seu celular."
    );

    return false;

  }


  /*
    Formato final:

    55 + DDD + 9 dígitos

    Total: 13 números
  */

  if (numeros.length !== 13) {

    mostrarErro(
      celularInput,
      erroCelular,
      "Digite um celular válido com DDD."
    );

    return false;

  }


  removerErro(
    celularInput,
    erroCelular
  );

  return true;

}


/* =========================================
   VALIDAÇÃO AO SAIR DO CAMPO
========================================= */

nomeInput.addEventListener(
  "blur",
  validarNome
);


emailInput.addEventListener(
  "blur",
  validarEmail
);


celularInput.addEventListener(
  "blur",
  validarCelular
);


/* =========================================
   REMOVE O ERRO ENQUANTO CORRIGE
========================================= */

nomeInput.addEventListener("input", function () {

  if (!erroNome.classList.contains("hidden")) {
    validarNome();
  }

});


emailInput.addEventListener("input", function () {

  if (!erroEmail.classList.contains("hidden")) {
    validarEmail();
  }

});


/* =========================================
   ENVIO
========================================= */

formCadastro.addEventListener(
  "submit",
  function (event) {

    event.preventDefault();


    const nomeValido =
      validarNome();

    const emailValido =
      validarEmail();

    const celularValido =
      validarCelular();


    /*
      Só continua se TODOS forem válidos.
    */

    if (
      !nomeValido ||
      !emailValido ||
      !celularValido
    ) {

      return;

    }


    const dados = {

      nome:
        nomeInput.value.trim(),

      email:
        emailInput.value
          .trim()
          .toLowerCase(),

      celular:
        celularInput.value

    };


    console.log(
      "Cadastro válido:",
      dados
    );


    /*
      Aqui futuramente:

      fetch(...)
          ↓
      backend Java
          ↓
      MongoDB
    */

  }
);