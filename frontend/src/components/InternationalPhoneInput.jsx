import PhoneInput from 'react-phone-number-input'
import flags from 'react-phone-number-input/flags'
import labels from 'react-phone-number-input/locale/pt-BR.json'
import 'react-phone-number-input/style.css'
import './international-phone.css'

// Campo de telefone internacional: seletor de país com bandeira + DDI,
// formatação automática do número no padrão do país selecionado e validação
// dinâmica via libphonenumber-js. Valor em E.164 ("+...") para o formulário.
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
  return (
    <label className={`field${className ? ` ${className}` : ''}`} style={style}>
      <span>{label}</span>
      <div className="international-phone" data-error={error ? 'true' : undefined}>
        <PhoneInput
          id={id}
          name={name}
          value={value}
          onChange={onChangeValue}
          country={country}
          onCountryChange={onCountryChange}
          defaultCountry={defaultCountry}
          labels={labels}
          flags={flags}
          international={false}
          smartCaret={inteligente}
          limitMaxLength
          autoComplete="tel"
          required={required}
          disabled={disabled}
          placeholder={placeholder}
        />
      </div>
      {(helper || error) && (
        <small className={error ? 'field-hint limit-reached' : 'field-hint'}>{error || helper}</small>
      )}
    </label>
  )
}