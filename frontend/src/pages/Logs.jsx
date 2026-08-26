import { useEffect, useState } from 'react'
import Table from '../components/Table.jsx'
import Pagination from '../components/Pagination.jsx'
import { logsApi } from '../api/logsApi.js'

const ITENS_POR_PAGINA = 20

function formatarDataHora(iso) {
  if (!iso) return '-----'
  const data = new Date(iso)
  if (Number.isNaN(data.getTime())) return '-----'
  return data.toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export default function Logs() {
  const [itens, setItens] = useState([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)
  const [pagina, setPagina] = useState(0)
  const [totalPaginas, setTotalPaginas] = useState(1)
  const [totalItens, setTotalItens] = useState(0)

  // Filtros
  const [dataFiltro, setDataFiltro] = useState('')
  const [usuarioFiltro, setUsuarioFiltro] = useState('')
  const [acaoFiltro, setAcaoFiltro] = useState('')

  useEffect(() => {
    let cancelado = false
    async function buscar() {
      setCarregando(true)
      setErro(null)
      try {
        const dataApi = await logsApi.listar({
          data: dataFiltro || undefined,
          usuario: usuarioFiltro.trim() || undefined,
          acao: acaoFiltro.trim() || undefined,
          page: pagina,
          size: ITENS_POR_PAGINA,
        })
        if (cancelado) return
        setItens(dataApi.content || [])
        setTotalPaginas(dataApi.totalPages || 1)
        setTotalItens(dataApi.totalElements || 0)
      } catch (err) {
        if (cancelado) return
        if (err.response?.status === 401) {
          window.dispatchEvent(new CustomEvent('meu-gendaz:logout'))
          return
        }
        setErro('Nao foi possivel carregar os logs. Tente novamente.')
        setItens([])
      } finally {
        if (!cancelado) setCarregando(false)
      }
    }
    const tempo = setTimeout(buscar, 300)
    return () => {
      cancelado = true
      clearTimeout(tempo)
    }
  }, [dataFiltro, usuarioFiltro, acaoFiltro, pagina])

  function aoMudarPagina(paginaUmBase) {
    setPagina(paginaUmBase - 1)
  }

  return (
    <section className="page reports-page">
      <div className="page-title">
        <span className="section-kicker">Auditoria</span>
        <h1>Logs de atividade</h1>
        <p>Registro de todas as acoes realizadas na sua empresa. Somente leitura.</p>
      </div>

      <div className="panel report-filters">
        <label className="field report-filter-field">
          <span>Data</span>
          <input
            type="date"
            value={dataFiltro}
            onChange={(e) => {
              setPagina(0)
              setDataFiltro(e.target.value)
            }}
            aria-label="Filtrar por data"
          />
          <small className="field-hint">&nbsp;</small>
        </label>

        <label className="field report-filter-field">
          <span>Usuário</span>
          <input
            type="text"
            placeholder="Buscar por nome"
            value={usuarioFiltro}
            onChange={(e) => {
              setPagina(0)
              setUsuarioFiltro(e.target.value)
            }}
            aria-label="Filtrar por usuário"
            maxLength={50}
          />
          <small className="field-hint">&nbsp;</small>
        </label>

        <label className="field report-filter-field">
          <span>Ação</span>
          <input
            type="text"
            placeholder="Buscar por descrição"
            value={acaoFiltro}
            onChange={(e) => {
              setPagina(0)
              setAcaoFiltro(e.target.value)
            }}
            aria-label="Filtrar por ação"
            maxLength={100}
          />
          <small className="field-hint">&nbsp;</small>
        </label>
      </div>

      {erro && <div className="gendaz-erro">{erro}</div>}

      <section className="panel">
        <div className="panel-head">
          <h2>Atividades</h2>
        </div>

        {carregando ? (
          <div className="space-y-3">
            <div className="h-12 animate-pulse rounded bg-gray-700" />
            <div className="h-72 animate-pulse rounded bg-gray-700" />
          </div>
        ) : (
          <Table
            columns={[
              {
                key: 'nomeUsuario',
                label: 'USUÁRIO',
                render: (row) => (
                  <div className="name-cell">
                    <div className="avatar">{(row.nomeUsuario || 'SG').substring(0, 2).toUpperCase()}</div>
                    <div className="name-cell-info">
                      <strong>{row.nomeUsuario || 'Sistema'}</strong>
                    </div>
                  </div>
                ),
              },
              {
                key: 'acao',
                label: 'AÇÃO',
                render: (row) => <span className="report-center-cell">{row.acao || '-'}</span>,
              },
              {
                key: 'dataHora',
                label: 'DATA / HORA',
                render: (row) => <span className="report-center-cell">{formatarDataHora(row.dataHora)}</span>,
              },
            ]}
            rows={itens}
            empty="Nenhuma atividade registrada ainda."
          />
        )}

        <Pagination
          page={pagina + 1}
          totalPages={totalPaginas}
          totalItems={totalItens}
          pageSize={ITENS_POR_PAGINA}
          onPageChange={aoMudarPagina}
        />
      </section>
    </section>
  )
}
