import { useEffect, useMemo, useRef, useState } from 'react'
import PhoneInput from 'react-phone-number-input'
import flags from 'react-phone-number-input/flags'
import labels from 'react-phone-number-input/locale/pt-BR.json'
import { getCountryCallingCode, parsePhoneNumberFromString } from 'libphonenumber-js'
import { obterExemploTelefone, obterNomePais, validarTelefone } from '../utils/phoneUtils.js'
import 'react-phone-number-input/style.css'
import './international-phone.css'

const PREFIXO_EXEMPLO_LEGADO = 'Exemplo para o país selecionado'

function GendazCountrySelect({ value: codigoBruto, options, onChange, disabled, readOnly, iconComponent: Icon }) {
  const [aberto, setAberto] = useState(false)
  const raizRef = useRef(null)

  const codigo = codigoBruto === 'ZZ' ? undefined : codigoBruto

  const ddis = useMemo(() => {
    const mapa = {}
    for (const opcao of options) {
      if (opcao && !opcao.divider && opcao.value) {
        try {
          mapa[opcao.value] = getCountryCallingCode(opcao.value)
        } catch {}
      }
    }
    return mapa
  }, [options])

  const selecionado = useMemo(
    () => options.find((o) => !o.divider && (o.value === codigo || (codigo == null && o.value === undefined))),
    [options, codigo]
  )

  useEffect(() => {
    if (!aberto) return undefined
    const aoClicarFora = (evento) => {
      if (raizRef.current && !raizRef.current.contains(evento.target)) setAberto(false)
    }
    const aoApertarTecla = (evento) => {
      if (evento.key === 'Escape') setAberto(false)
    }
    document.addEventListener('mousedown', aoClicarFora)
    document.addEventListener('keydown', aoApertarTecla)
    return () => {
      document.removeEventListener('mousedown', aoClicarFora)
      document.removeEventListener('keydown', aoApertarTecla)
    }
  }, [aberto])

  function escolher(codigoNovo) {
    onChange(codigoNovo)
    setAberto(false)
  }

  const bloqueado = disabled || readOnly
  const ddiAtual = codigo ? ddis[codigo] : ''

  return (
    <div className="gd-phone-country" ref={raizRef}>
      <button
        type="button"
        className="gd-phone-country__toggle"
        disabled={bloqueado}
        aria-haspopup="listbox"
        aria-expanded={aberto}
        onClick={() => setAberto((valor) => !valor)}
      >
        <span className="gd-phone-country__flag">
          {codigo ? <Icon country={codigo} label={selecionado?.label} /> : null}
        </span>
        {ddiAtual && <span className="gd-phone-country__ddi">+{ddiAtual}</span>}
        <span className={`gd-phone-country__arrow${aberto ? ' gd-phone-country__arrow--open' : ''}`} aria-hidden="true" />
      </button>

      {aberto && !bloqueado && (
        <div className="gd-phone-dropdown" role="listbox">
          <div className="gd-phone-dropdown__list">
            {options
              .filter((o) => o && !o.divider)
              .map((opcao) => {
                const codigoOpcao = opcao.value
                const ativa = (codigoOpcao || undefined) === (codigo || undefined)
                return (
                  <button
                    key={codigoOpcao || '__internacional__'}
                    type="button"
                    role="option"
                    aria-selected={ativa}
                    className={`gd-phone-dropdown__option${ativa ? ' gd-phone-dropdown__option--selected' : ''}`}
                    onClick={() => escolher(codigoOpcao)}
                  >
                    <span className="gd-phone-dropdown__flag">
                      {codigoOpcao ? <Icon country={codigoOpcao} label={opcao.label} /> : null}
                    </span>
                    <span className="gd-phone-dropdown__name">{opcao.label}</span>
                    <span className="gd-phone-dropdown__ddi">{codigoOpcao ? `+${ddis[codigoOpcao] || ''}` : ''}</span>
                  </button>
                )
              })}
          </div>
        </div>
      )}
    </div>
  )
}

// Campo de telefone internacional: seletor de país com bandeira + DDI dentro da
// mesma borda do input, formatação automática via libphonenumber-js e validação
// dinâmica. Valor em E.164 ("+...") para o formulário.
export default function InternationalPhoneInput({
  label = 'Telefone',
  helper,
  error,
  value,
  onChangeValue,
  country,
  onCountryChange,
  defaultCountry = 'BR',
  required = false,
  disabled = false,
  id,
  name,
  placeholder,
  inteligente = true,
  style,
  className,
}) {
  const [paisEscolhido, setPaisEscolhido] = useState(defaultCountry)

  const paisInferido = useMemo(() => {
    try {
      const numero = parsePhoneNumberFromString(String(value || ''))
      if (numero && numero.country) return numero.country
    } catch {}
    return null
  }, [value])

  const paisEfetivo = (country || paisInferido || paisEscolhido || defaultCountry || 'BR').toUpperCase()

  const exemplo = useMemo(() => obterExemploTelefone(paisEfetivo), [paisEfetivo])
  const nomePaisTexto = obterNomePais(paisEfetivo)
  const mensagemExemplo = exemplo
    ? `Exemplo para ${nomePaisTexto}: ${exemplo}`
    : `Exemplo para ${nomePaisTexto}: informe um número válido`

  const valorPreenchido = Boolean(value && String(value).replace(/\D/g, ''))
  const textoInvalido = valorPreenchido ? validarTelefone(String(value), paisEfetivo, false) : ''

  const helperEhLegado = typeof helper === 'string' && helper.startsWith(PREFIXO_EXEMPLO_LEGADO)

  let mensagem = mensagemExemplo
  let exibirErro = false

  if (error) {
    mensagem = error
    exibirErro = true
  } else if (textoInvalido) {
    mensagem = textoInvalido
    exibirErro = true
  } else if (helper && !helperEhLegado) {
    mensagem = helper
  }

  function aoMudarPais(novoPais) {
    setPaisEscolhido(novoPais)
    if (onCountryChange) onCountryChange(novoPais)
  }

  return (
    <label className={`field${className ? ` ${className}` : ''}`} style={style}>
      <span>{label}</span>
      <div className="international-phone" data-error={exibirErro ? 'true' : undefined}>
        <PhoneInput
          id={id}
          name={name}
          value={value}
          onChange={onChangeValue}
          country={country}
          onCountryChange={aoMudarPais}
          defaultCountry={defaultCountry}
          labels={labels}
          flags={flags}
          countrySelectComponent={GendazCountrySelect}
          international={false}
          smartCaret={inteligente}
          limitMaxLength
          autoComplete="tel"
          required={required}
          disabled={disabled}
          placeholder={placeholder}
        />
      </div>
      {mensagem && (
        <small className={exibirErro ? 'field-hint limit-reached' : 'field-hint'}>{mensagem}</small>
      )}
    </label>
  )
}