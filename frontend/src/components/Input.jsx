export default function Input({ label, helper, ...props }) {
  const hasCounter = props.maxLength && typeof props.value === 'string'
  const currentLength = hasCounter ? props.value.length : 0
  const limitReached = hasCounter && currentLength >= Number(props.maxLength)

  return (
    <label className="field">
      <span>{label}</span>
      <input {...props} />
      {(helper || hasCounter) && (
        <small className={limitReached ? 'field-hint limit-reached' : 'field-hint'}>
          {limitReached ? 'Limite de caracteres atingido.' : helper}
          {hasCounter && <strong>{currentLength}/{props.maxLength}</strong>}
        </small>
      )}
    </label>
  )
}
