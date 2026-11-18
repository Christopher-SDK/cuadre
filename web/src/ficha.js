// Ficha de un bug: el caso se asienta paso a paso (FIRMA-05) y el impacto baja entero si no cabe.
import { observar, pliegue } from './comun.js';

const pasos = document.getElementById('pasos');
const resultado = document.getElementById('resultado');
function reproducir() {
  [pasos, resultado].forEach((el) => { el.classList.remove('reproduce'); void el.offsetWidth; el.classList.add('reproduce'); });
}
document.getElementById('reproducir').addEventListener('click', reproducir);
observar('#pasos', reproducir, '0px 0px -15% 0px');

pliegue(document.querySelector('.ficha__heroe'), [document.getElementById('impacto')], document.getElementById('extra'));
