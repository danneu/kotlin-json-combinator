
const MAX_ARITY = 20

const TAB_SIZE = 4

function mapTemplate (currArity, maxArity) {
  const prefix = Array.apply(null, { length: currArity * TAB_SIZE } ).map(() => ' ').join('')
  if (currArity === maxArity) {
    return `${prefix}d${currArity}(value).map { v${currArity}: V${currArity} ->
        ${prefix}f(${Array.apply(null, { length: maxArity }).map((_, i) => `v${i + 1}`).join(', ')})
    ${prefix}}`
  } else {
    return `${prefix}d${currArity}(value).flatMap { v${currArity}: V${currArity} ->
    ${mapTemplate(currArity + 1, maxArity)}
    ${prefix}}`
  }
}

function template (arity) {
  // 'V1, ..., V20'
  const gen1 = Array.apply(null, { length: arity })
    .map((_, i) => `V${i + 1}`)
    .join(', ')
  // 'd1: Decoder<V1>, ..., d20: Decoder<V20>'
  const gen2 = Array.apply(null, { length: arity })
    .map((_, i) => `d${i + 1}: Decoder<V${i + 1}>`)
    .join(', ')

  return `
fun <${gen1}, T> map(f: (${gen1}) -> T, ${gen2}): Decoder<T> {
    return Decoder { value: JsonValue ->
    ${mapTemplate(1, arity)}
    }
}`
}

function gen () {
  const allCode = Array.apply(null, { length: MAX_ARITY }).map((_, i) => {
    return template(i + 1)
  }).join('\n')

  console.log(allCode)
}

gen()
