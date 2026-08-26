export function formatoCompactoReceita(valor) {
  if (!valor || valor <= 0) return 'R$ 0'
  if (valor >= 1000) {
    const milhar = valor / 1000
    const texto = milhar >= 100
      ? Math.round(milhar)
      : (milhar % 1 === 0 ? milhar : milhar.toFixed(1).replace('.', ','))
    return `R$ ${texto}k`
  }
  return `R$ ${Math.round(valor)}`
}
