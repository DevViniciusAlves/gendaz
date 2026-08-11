import { useEffect, useState } from 'react'
import Button from './Button.jsx'
import Modal from './Modal.jsx'

function emitirToast(type, message) {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent('gendaz:toast', {
    detail: { type, message },
  }))
}

/**
 * Modal reutilizÃ¡vel de exportaÃ§Ã£o CSV.
 *
 * Responsabilidades: exibir as opÃ§Ãµes (tudo / perÃ­odo), validar as datas,
 * confirmar ou cancelar, controlar o loading e exibir erros.
 *
 * NÃ£o conhece regras de domÃ­nio (clientes, financeiro, relatÃ³rios): a pÃ¡gina
 * decide o que exportar atravÃ©s de `onConfirm`, que deve retornar uma Promise.
 * Em caso de erro (inclusive "nenhum registro"), a Promise deve rejeitar e o
 * modal permanece aberto exibindo a mensagem.
 */
export default function ExportCsvModal({
  open = false,
  title = 'Exportar CSV',
  allowAll = true,
  allowPeriod = true,
  onClose,
  onConfirm,
}) {
  const [modo, setModo] = useState('tudo')
  const [dataInicial, setDataInicial] = useState('')
  const [dataFinal, setDataFinal] = useState('')
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState('')

  useEffect(() => {
    if (!open) return
    setModo('tudo')
    setDataInicial('')
    setDataFinal('')
    setErro('')
    setCarregando(false)
  }, [open])

  function validarPeriodo() {
    if (!dataInicial) return 'Informe a data inicial.'
    if (!dataFinal) return 'Informe a data final.'
    if (dataInicial > dataFinal) return 'A data inicial nÃ£o pode ser posterior Ã  data final.'
    return ''
  }

  async function confirmar() {
    if (carregando) return
    setErro('')

    const ehPeriodo = modo === 'periodo'
    if (ehPeriodo) {
      const mensagemErro = validarPeriodo()
      if (mensagemErro) {
        setErro(mensagemErro)
        return
      }
    }

    setCarregando(true)
    try {
      await onConfirm?.({
        modo,
        dataInicial: ehPeriodo ? dataInicial : null,
        dataFinal: ehPeriodo ? dataFinal : null,
      })
      emitirToast('success', 'Arquivo CSV exportado com sucesso.')
      onClose?.()
    } catch (error) {
      const mensagem = error?.response?.data?.mensagem
        || error?.message
        || 'NÃ£o foi possÃ­vel gerar a exportaÃ§Ã£o.'
      setErro(mensagem)
    } finally {
      setCarregando(false)
    }
  }

  // Impede fechar o modal enquanto a exportaÃ§Ã£o estÃ¡ em andamento.
  const handleClose = carregando ? () => {} : onClose

  return (
    <Modal title={title} open={open} onClose={handleClose}>
      <div className="export-csv-modal">
        <div className="export-options">
          {allowAll && (
            <label className="export-option">
              <input
                type="radio"
                name="export-tipo"
                checked={modo === 'tudo'}
                onChange={() => setModo('tudo')}
              />
              <span>
                <strong>Exportar tudo</strong>
                <small>Todos os registros disponÃ­veis da empresa.</small>
              </span>
            </label>
          )}
          {allowPeriod && (
            <label className="export-option">
              <input
                type="radio"
                name="export-tipo"
                checked={modo === 'periodo'}
                onChange={() => setModo('periodo')}
              />
              <span>
                <strong>Selecionar perÃ­odo</strong>
                <small>Somente registros dentro do intervalo informado.</small>
              </span>
            </label>
          )}
        </div>

        {modo === 'periodo' && (
          <div className="export-period">
            <label className="field">
              <span>Data inicial</span>
              <input
                type="date"
                value={dataInicial}
                max={dataFinal || undefined}
                onChange={(e) => setDataInicial(e.target.value)}
                aria-label="Data inicial do perÃ­odo"
              />
            </label>
            <label className="field">
              <span>Data final</span>
              <input
                type="date"
                value={dataFinal}
                min={dataInicial || undefined}
                onChange={(e) => setDataFinal(e.target.value)}
                aria-label="Data final do perÃ­odo"
              />
            </label>
          </div>
        )}

        {erro && <p className="form-error">{erro}</p>}

        <div className="confirm-actions">
          <Button variant="secondary" onClick={handleClose} disabled={carregando}>
            Cancelar
          </Button>
          <Button onClick={confirmar} loading={carregando} loadingText="Exportando...">
            Exportar
          </Button>
        </div>
      </div>
    </Modal>
  )
}

