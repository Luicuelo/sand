# El simulador de materiales: reglas y sistema

Documento de referencia del simulador. Explica **cómo está montado** y, sobre
todo, **por qué** cada pieza es como es: la mayoría de las decisiones de aquí
salieron de un fallo concreto, así que cada una viene con su motivo.

Si dentro de un mes algo no cuadra, el orden de lectura recomendado es: las
secciones 4 (la célula), 5 (la vida) y 7 (cómo se aplica una regla). Con esas
tres se puede leer cualquier regla de la tabla y saber qué hace.

---

## 1. La idea en una frase

Cada célula guarda **un número** (un índice de paleta) que dice a la vez *de qué
material es* y *cuánta vida le queda*. En cada paso, y solo cuando su reloj se lo
marca, cada partícula busca en una tabla la **primera regla que encaja**
con su vecindad y el resultado de esa regla dice qué se escribe en la generación
siguiente.

La regla es un par de dibujos de texto:

```
lo que hay ahora        lo que se escribe después
    ?F?                     ???
    ?M?          →          ?W?
    ???                     ???
```

Eso es madera con fuego encima: la madera envejece un paso. No hay código para
"la madera arde": hay un dibujo que lo dice.

---

## 2. Los cuatro archivos

| Archivo | Responsabilidad |
|---|---|
| [MaterialSimulator.java](src/principal/MaterialSimulator.java) | Ventana, barra de materiales, teclado, bucle de 60 FPS |
| [WorldRenderer.java](src/principal/WorldRenderer.java) | Doble búfer, paleta, pincel del ratón, golpes, el reloj de cada material y el del dibujo, pintado a píxeles |
| [MaterialRules.java](src/principal/MaterialRules.java) | Leer vecindades y aplicar las reglas: el motor, literalmente |
| [Material.java](src/principal/Material.java) | La tabla de materiales: glifo, banda de valores, vida, reloj y colores |

Toda la configuración vive en dos sitios: la lista `Material.LIST` (qué
materiales existen) y la tabla `Pattern.STEPS` (qué hacen). El resto del código
es maquinaria que no hay que tocar para añadir cosas.

Fuera de este paquete está `src/sand`, el motor original de arena recuperado del
commit `a2679ca` y sin tocar (`sand.Sand`). Compila en el mismo proyecto pero no
comparte nada con estos cuatro archivos: es la referencia de dónde venimos.

---

## 3. Un fotograma, paso a paso

`WorldRenderer.tick()` se ejecuta 60 veces por segundo, y cada llamada es **un
fotograma**: `ENGINE_MULTIPLIER` pasos del mundo (hoy 9) y, cuando le toca por su
cuenta, un pintado. El multiplicador se toca solo y acelera todo a la vez, porque
está por encima de los relojes de los materiales: no cambia ninguna regla.

1. **Golpe** (si hay uno activo): `pushParticles()` empuja lo que haya hacia un
   lado y el resultado pasa a ser la generación actual. El golpe se mide en
   fotogramas, que es lo que dura un gesto del ratón, así que el multiplicador no
   lo acelera. Empuja lo que está suelto: los materiales de `Material.ANCHORED`
   —hoy solo la madera— se quedan donde están y ni siquiera se les tira el dado,
   y como su celda no queda libre, la partícula que se apoya en ellos tampoco
   avanza. El golpe tiene su propio paso (`MaterialRules.pushParticle`) y no lee
   la tabla, así que esa excepción se declara en el material y no como una regla:
   `WOOD_STAYS` no la protege.
2. **Pasos del mundo**, repetidos `ENGINE_MULTIPLIER` veces:
   - **Pincel**: lo que diga el pincel elegido, en la celda del ratón o en un
     cuadrado de 2 celdas de radio si se mantiene SPACE. Hay tres: una partícula
     del material seleccionado, **nada** —para mover el ratón por el mundo sin
     dibujar— y **vacío**, el borrador, que vacía la celda. Los dos últimos se
     llaman con los glifos que las reglas ya usan para eso mismo en un
     `result`: `?` no escribe nada y `_` vacía la celda. Un material va dentro del
     paso y con el reloj del material, pero al doble: suelta un grano cada dos
     turnos del material seleccionado, que son veinticuatro pasos para la arena y
     dos para el agua (ver "El reloj de cada material"). El borrador no espera
     reloj ninguno, porque no apila nada encima de lo que acaba de quitar.
   - **Barrido**: se recorre la rejilla fila por fila y, en cada celda que no esté
     ya resuelta en el búfer siguiente, se pregunta a su material si le toca en
     este paso (`Material.isDue`). Si le toca, se llama a
     `MaterialRules.stepParticle()`, que aplica la primera regla que encaja y
     escribe en el búfer siguiente; si no le toca, la celda se copia tal cual. Una
     celda con algo ya escrito en el búfer siguiente no tiene turno: o estaba
     vacía —y no había nada que hacer— o la escribió una regla que llegó antes,
     como la otra mitad de un cambio (§7.4).
   - **Intercambio**: `advanceGeneration()` cambia `cells` por `nextCells` y vacía
     el que queda libre. Nunca se copian datos: se intercambian las referencias.
3. **Pintado** (`FRAMES_PER_PAINT = 1`): la imagen se rehace en todos los
   fotogramas. Con varios pasos por fotograma el mundo se movía varias celdas
   entre imagen e imagen y se notaba el salto, así que el pintado va a la par del
   fotograma y no del paso. La rejilla se expande a bloques de 8x8 píxeles en un
   `byte[]` BGRA y se manda a la imagen en una sola llamada.

**Por qué el multiplicador es impar.** El agua da un paso en cada paso del mundo,
así que con 9 cae **nueve celdas entre fotograma y fotograma**, y el pincel suelta
una gota cada dos pasos: el chorro es una cadena de gotas separadas dos celdas. Con
el multiplicador en 8 cada imagen desplazaba la cadena ocho filas, un múltiplo
exacto de esa separación, así que las gotas caían siempre en filas de la misma
paridad: el chorro se veía como una línea discontinua **congelada** en el aire, con
las filas de la otra paridad siempre vacías en el instante del pintado (el efecto de
la rueda del carro). Con 9 la cadena avanza una celda respecto de su propia red en
cada imagen y se ve caer. Lo que importa no es que el multiplicador sea impar sino
el producto **`ENGINE_MULTIPLIER × FRAMES_PER_PAINT`**, porque el pintado se salta
fotogramas: con el multiplicador en 9 y el pintado de dos en dos el producto vuelve
a ser par y el chorro se congela otra vez.

### El reloj de cada material

El renderer lleva dos contadores y cada uno tiene su trabajo. `frameCounter`
cuenta **fotogramas** y lo dividen el golpe y el pintado; `stepCounter` cuenta
**pasos del mundo** y lo dividen los materiales y el pincel. Con el multiplicador
en 9 hay nueve pasos por fotograma, es decir, 540 pasos por segundo.

Cada material guarda cada cuántos pasos da uno de los suyos y cuántos de esos
turnos tarda en gastar un valor de su banda. El primer número es el **reloj** y va
después del `spread` en `Material.LIST`; el segundo es la **vejez** y va justo
detrás. El agua va con reloj 1 y da un paso en todos los pasos (540 por segundo);
la arena va con 12 y da uno de cada doce (45 por segundo), así que cae y se abre a
un doceavo de la velocidad del agua. La madera y el fuego van con 22, para que una
llama suba una fila por cada valor que gasta la madera y el frente de combustión no
se despegue, y el humo con 9, que es una vez por fotograma. El ácido va con 2, y
es el único líquido que no cae como el agua: se mueve y muerde a la mitad de
velocidad, y así se lee como un líquido pesado y no como un segundo agua. Lo que
se gasta despacio en él es la carga, un bocado por turno al corroer y uno cada
sesenta turnos al diluirse (§5).

La partícula que no tiene el turno **no se omite del todo**: su celda se copia al
búfer siguiente. No es un extra, es obligatorio, porque el barrido escribe en el
otro búfer y la que no se copiara desaparecería. Copiada, conserva su sitio, su
color y su vida hasta que le vuelva a tocar.

**El pincel usa el mismo reloj, al doble.** `dropUnderMouse()` se llama dentro del
paso y suelta un grano cada **dos** turnos del material seleccionado
(`stepCounter % (stepPeriod * 2)`): uno cada veinticuatro pasos para la arena (una
vez cada dos fotogramas y dos tercios), uno cada dos para el agua (cuatro y medio
por fotograma), uno cada cuatro para el ácido, uno cada cuarenta y cuatro para la
madera y el fuego y uno cada dieciocho para el humo. Dos turnos y no uno porque un
grano caído en el mismo turno en que
nace aparece en la celda que el anterior acaba de dejar, y el `match` mira la
generación anterior:
todavía lo ve ahí, no cae y se desliza en diagonal. Con un turno de hueco en medio
siempre nace sobre celda libre y el chorro baja recto. Es el mismo truco que hacía
`DROP_EVERY_FRAMES = 2` antes de que existieran los relojes, pero atado al material
en vez del fotograma: medido, el chorro pasa de 3 celdas de ancho a 1.

**El borrador va en todos los pasos.** Los dos turnos de espera de arriba existen
para que un grano no nazca en la celda que el anterior acaba de dejar; el borrador
no pone nada, así que no tiene ese problema y escribe vacío en cada paso, que es la
velocidad del ratón. Con SPACE vacía el mismo cuadrado de 2 celdas de radio, y
vaciar dos veces la misma celda no cuesta nada.

**Las teclas del pincel.** Cada material tiene la suya, que es su glifo, pero los
otros dos no son glifos de material y no caben en esa regla: **Esc** elige el pincel
que no pinta nada y **Supr** el borrador. Pulsar la tecla selecciona el botón y no
escribe nada más: el botón avisa al renderer por el mismo oyente que los materiales,
así que el pincel y el título de la ventana se enteran igual que con el ratón. Y Esc
no es solo una tecla cómoda: es la que se busca cuando el pincel se queda pegado en
algo que no se quiere pintar.

**El barrido alterna por fotograma, no por paso.** El sentido del barrido es lo que
reparte la ventaja cuando dos partículas quieren la misma celda, y solo sirve
alternando *entre turnos de la misma partícula*. Como los turnos de un material se
cuentan en pasos y el sentido se cambia por fotograma, lo que hay que mirar es en
qué fotogramas caen sus turnos: la arena da uno cada 12 pasos y un fotograma tiene
9, así que sus turnos caen en los fotogramas 0, 1, 2, 4, 5, 6, 8, 9… y ven los dos
sentidos. Los materiales más rápidos que el fotograma (el agua, con 1, y el ácido,
con 2) se evalúan varias veces por fotograma y acaban viendo los dos sentidos de
todas formas. El humo, con 9, cae una vez por fotograma, en el primer paso de
cada uno, y también los ve. La madera y el fuego, con 22, caen en los pasos 0, 22,
44, 66…, es decir en los fotogramas 0, 2, 4, 7, 9, 11, 13, 16…: el cociente
22 / 9 = 2,44 tampoco es entero y alterna. Si el cociente
`reloj / multiplicador` fuera entero y par, todos los turnos caerían del mismo
lado: medido con la arena en 12 y el
multiplicador en 2 (cociente 6), el bloque se iba 5,4 celdas a un lado y se
aplanaba (13 de alto y 42 de ancho, frente a 17 y 37). Hoy 12 / 9 no es entero
(1,33), que es justo lo que hace falta.

### Dos detalles del barrido que importan

- **Doble búfer**: `cells` es lo que se lee, `nextCells` lo que se escribe. Nadie
  mira `nextCells` salvo para comprobar si una celda sigue libre (`isFree`) y para
  saber qué celdas ya están resueltas (`stepCell` se salta las que no están
  vacías). Gracias a eso una partícula no puede "verse" a sí misma dos veces, y un
  cambio puede escribir la celda que todavía no ha llegado a su turno (§7.4).
- **El sentido del barrido se alterna** cada fotograma (`frameCounter & 1`).
  Cuando dos partículas quieren la misma celda, gana la que se lee primero; con
  un sentido fijo, la misma partícula ganaría siempre y las pilas crecerían
  torcidas hacia un lado. Alternando, la simetría se conserva (comprobado: el
  centroide de un montón se queda a menos de una celda del centro).

---

## 4. La célula: un solo `int`

`cells[y][x]` es un índice de la paleta de 256 colores:

| Valor | Significado |
|---|---|
| `0` | vacío (se dibuja blanco) |
| `1` a `107` | un material, con su vida |
| resto | sin uso |

Que el material y la vida sean **el mismo número** es la decisión central del
diseño: no hay un array de materiales al lado del de vidas que se puedan
desincronizar. Si una partícula envejece, cambia de color; si cambia de color,
es que ha envejecido.

Consecuencia inmediata: **el 0 no puede ser un material**, porque 0 es vacío en
todo el motor. Por eso las bandas empiezan en 1 (y la idea original de "arena de
0 a 10" no era viable).

`Material.glyphOf[256]` es la tabla inversa (valor → glifo) que consulta el
`match`: es lo que permite escribir `F` en un patrón y que coincida con
cualquiera de los 10 valores del fuego.

### Los materiales

| Glifo | Material | Valores | Vida | Nace en | Reloj | Vejez | Rampa |
|---|---|---|---|---|---|---|---|
| `S` | arena | 1-10 | no | 1-10 | 12 | 1 | amarillo pálido → ocre dorado |
| `A` | agua | 11-67 | no (la cola de la banda es la espera) | 11-18 | 1 | 1 | azul medio → azul claro, y la espera plana |
| `M` | madera | 68-83 | sí (16 turnos, 352 pasos) | 68-71 | 22 | 1 | madera clara → madera oscura |
| `F` | fuego | 84-93 | sí (30 turnos, 30 filas) | 84-86 | 22 | 3 | amarillo pálido → naranja → rojo |
| `H` | humo | 94-101 | sí (79 turnos, 75 filas) | 94-95 | 9 | 13 | gris oscuro → gris claro |
| `C` | ácido | 102-107 | sí (la banda es la carga: seis bocados, y al diluirse uno cada sesenta turnos, que son ciento veinte pasos) | 102-103 | 2 | 60 | verde pálido → verde oscuro |

Las bandas se asignan solas en el orden de la lista, desde 1. Añadir un material
es añadir una línea; los valores se recalculan.

La banda del agua tiene dos tramos que no son lo que parecen. Los ocho primeros
valores son los tonos con los que nace una gota (`spread = 8`), y de ahí al último
la banda **es la espera** de un grano: la gota que tiene arena encima recorre esos
valores de uno en uno con el `W` (§8) y el grano se hunde cuando llega al final. Por
eso las paradas de color del tramo de espera son casi la misma: la cuenta avanza sin
que el charco cambie de color. Como no tiene vida, tampoco se pinta el fantasma
blanco del último valor y la espera no parpadea. Y el ancho está medido: una gota
nacida en cualquiera de los ocho primeros valores tarda entre 49 y 56 pasos en
llegar al final, que son más de cuatro turnos del grano (48) y menos de cinco
(60), así que todo grano se hunde en su quinto turno y una fila cuesta siempre 60
pasos del mundo: 6,7 fotogramas con el multiplicador en 9.

El ancho de cada banda no es decorativo, pero **tampoco es la vida**: la vida son
los turnos, y un turno cuesta una fila. Lo que convierte valores en turnos es la
**vejez** (`ages`): cuántos turnos tarda en gastarse un valor de la banda. Así, la
vida de un material es su ancho por su vejez, en turnos, y la altura que alcanza es
esa misma cifra en filas. Por eso el humo llega a las 75 filas del lienzo con 8
valores: gasta un valor cada 13 turnos y sube una fila por turno. El fuego, que se
apaga a un tercio de pantalla, necesita 30 turnos y los saca de 10 valores con vejez
3. La madera no sube: sus 16 valores con vejez 1 y reloj 22 son 352 pasos de
combustión.

La vejez no toca la velocidad: el humo sigue subiendo una fila por turno y su reloj
sigue siendo 9. Lo que cambia es cada cuánto pierde un tono, y eso se ve en el
fantasma del último valor: dura lo que dura un escalón, así que el humo es casi
blanco los últimos 13 de sus 79 turnos. Cuanto más corta la banda, más pesa el
fantasma, y por eso la vejez no se puede subir sin mirar la pantalla.

La arena, en cambio, con reloj 12 baja una fila cada doce pasos, es decir, una
cada fotograma y un tercio con el multiplicador en 9.

La banda del ácido tiene el tercer oficio: es la **carga**. Se gasta un valor por
cada celda de madera que come y uno cada sesenta turnos —ciento veinte pasos,
con su reloj en 2— por cada agua que lo diluye; caer, fluir o esperar no quitan
ni un tono, así que un charco de ácido
quieto es estable para siempre y una gota vale seis bocados como mucho. El sorteo
de nacimiento le quita uno o ninguno, y el tono va del verde pálido al verde
oscuro del poso, así que la gota dice lo que le queda sin ninguna marca aparte.

Su **vejez 60 no es una vida: es la paciencia del agua.** La corrosión no la
espera —la regla escribe `r`, que cobra el bocado en el acto—, pero la dilución
escribe `W`, que sólo avanza cuando la vejez le da el turno. De la misma banda
salen así dos velocidades: una gota que muerde gasta un valor por turno y una
gota que el agua está comiendo gasta uno cada sesenta turnos, y como un turno son
dos pasos el valor sigue costando ciento veinte pasos y la gota entera
seiscientos. El 60 y no 120 sale del reloj: la vejez se cuenta en turnos del
material, así que al subir el reloj a 2 hubo que bajar la vejez a la mitad para
que la química no se hiciera el doble de lenta. Eso es lo que convierte la
neutralización en un proceso y no en un parpadeo (§8). Seis bocados
y no ochenta es lo que impide que un chorro disuelva un bosque: medido, un chorro
de doscientas gotas sobre un tronco de 440 celdas come 122 en 2.400 pasos y a los
4.800 ya no queda ácido.

---

## 5. La vida es la posición en la banda

Para un material con vida, la posición dentro de su banda **es** su edad. Cuatro
glifos la mueven, y solo cuatro (más uno que la sortea de nuevo, `B`, al final de
esta sección):

| Glifo | Efecto | Para qué |
|---|---|---|
| `V` | +1, y al llegar al final vuelve al **primer** valor | Ciclos sin fin, siempre con el mismo tono de arranque |
| `W` | +1, y al llegar al final **se queda ahí** | Envejecer hasta morir; sólo avanza cuando la vejez le da el turno |
| `r` | Se salta a la mitad de la vida que le **queda** | Quemar deprisa |
| `l` | Retrocede a la mitad de la vida que ya **gastó** | Rejuvenecer / alimentar |
| `B` | La partícula con un valor **sorteado** entre los primeros de su banda | Volver a empezar sin que todo acabe del mismo color. Hoy ninguna regla lo usa: el cambio de sitio mueve el valor que la partícula ya tenía (§7.4) |

`B` es a `V` lo que `spawn()` a un valor fijo: los dos vuelven al principio de la
banda, pero `B` sortea el tono. Se escribe con mayúscula y **no** es una letra de
material, aunque lo parezca: igual que `X`, `V` y `W`, es una orden de escritura.
Su diferencia con una letra de material es la de siempre: una letra **crea** una
partícula nueva y deja sin reclamar la que se estaba moviendo, que al final del
paso se escribe en su propia celda (§7.2, salida 5).

`W` es el único de los cinco que **espera a la vejez** (`Material.isAgingDue`): en
los turnos en que no le toca, escribe la partícula con el valor que ya tenía. Eso es
lo que deja que un material se mueva en todos los turnos y envejezca en uno de cada
trece, y por eso una banda de 8 valores puede subir las 75 filas del lienzo. `r` y
`l` no esperan: son sucesos de la regla —quemar, alimentar— y no el paso del
tiempo.

Detalles que no son obvios:

- `W` **no escribe 0** al final: se queda en el último valor. Morir nunca es un
  efecto colateral de moverse; morir es siempre una regla escrita (`f → H`). Eso
  es lo que hace que la transformación sea explícita y legible.
- `r` y `l` siempre se mueven **al menos un paso** (`max(1, ...)`), aunque la
  mitad salga 0. Si no, un valor pegado al borde se quedaría inmóvil para
  siempre.
- **El último valor de un material con vida se pinta casi blanco** (85 % hacia
  blanco, en `WorldRenderer.Palette`). Es un **fantasma**: el material sigue ahí,
  invisible, esperando la regla que lo transforme. Por eso la madera parece
  desaparecer justo antes de arder. Dura lo que dura un escalón de la banda: un
  turno si la vejez es 1, trece si es 13 y ciento veinte si es 120. En el ácido no
  se ve nunca, porque `ACID_END` lo convierte en agua en el mismo paso en que
  aparece.
- Ningún material nace con un valor fijo: `spawn()` sortea entre los primeros
  valores de su banda (`spread`), para que un montón no sea una plancha de un
  solo color. Sin vida eso solo cambia el tono. Con vida el valor **es** la edad,
  así que el sorteo es también vida gastada al nacer, y por eso el margen se
  queda corto y distinto en cada material: la madera sortea entre 4 valores de 16
  (y por eso arde un poco antes), el fuego entre 3 de 10, y el humo entre 2 de 8,
  que es justo lo que se puede gastar sin dejar de llegar al techo. Cuanto más
  corta la banda, más pesa el sorteo: con 8 valores, sortear 6 dejaría humo de 26
  filas al lado de humo de 79.

### La banda también es una carga

La madera y el humo gastan la banda con el tiempo, el agua la gasta esperando y
el ácido la gasta **haciendo lo suyo**: un valor por cada celda de madera que
corroe y uno cada sesenta turnos por cada agua que lo diluye; ninguno por
caer, fluir o quedarse quieto. Es la misma mecánica de `W` —la posición en la
banda como contador— con una diferencia: el turno que la mueve no es el reloj, es
la reacción. Y aquí hay **dos** reacciones con dos prisas distintas, que es la
razón de que el ácido use dos glifos de vida y no uno: el bocado es un suceso y
se cobra en el acto con `r`, que no espera a nadie; la dilución es una espera y
se cobra con `W`, que sólo avanza cuando la vejez da el turno (§8).

De ahí que su vejez sea 60 y no 1. Con vejez 1 el agua convertiría la gota en
cinco turnos y un charco se haría agua en un par de fotogramas, que es justo lo
que se quería evitar; con 60 cada valor cuesta sesenta turnos —ciento veinte
pasos, porque el reloj es 2— y la gota entera seiscientos pasos, así que la
neutralización se ve y el ácido aguanta varias partidas dentro de un pozo. La
corrosión, en cambio, no mira la vejez: muerde en cada turno, y con el reloj en 2
muerde la mitad de las veces por segundo, aunque a lo largo de la vida de la gota
come lo mismo.

### La cadena completa de una pared de madera

```
madera  M (68..83)  --W-->  m (83, fantasma)
                            --WOOD_END-->  F (84, fuego nuevo)
                                           --W x3-->  f (93, fantasma)
                                                      --FIRE_END-->  H (94, humo)
                                                                     --W x13-->  h (101, fantasma)
                                                                                 --SMOKE_END-->  vacío
```

Cada flecha es una regla de la tabla. Ninguna transición está escondida en el
código.

---

## 6. El vocabulario de glifos

El mismo carácter significa cosas distintas según el dibujo en el que esté, y
esa es la clave del sistema: **un glifo es una condición en el `match` y una
orden de escritura en el `result`**.

### En el `match` (la condición)

| Glifo | Encaja con |
|---|---|
| `?` | cualquier cosa (ni se evalúa) |
| `X` | cualquier partícula; los bordes laterales e inferior cuentan como partícula |
| `_` | hueco; **por encima de la rejilla todo cuenta como hueco** |
| `S` `A` `M` `F` `H` `C` | cualquier valor de ese material |
| `s` `a` `m` `f` `h` `c` | **solo el último valor** de ese material: existe pero ya no tiene vida (en el agua, el que dice que ya ha esperado bastante; en el ácido, el que ya no tiene con qué morder y que la regla siguiente convierte en agua) |

### En el `result` (la escritura)

| Glifo | Qué escribe |
|---|---|
| `?` | nada (deja la celda como esté) |
| `_` | vacío |
| `X` | la partícula, con su valor exacto |
| `S` `A` `M` `F` `H` `C` | una partícula **nueva** de ese material, con `spawn()`, en la celda libre o en el sitio de una partícula que ya está ahí (§7.5) |
| `x` | la partícula, sorteada entre todas las `x` del resultado |
| `o` | una partícula **nueva** con una parte de la vida que le queda a la partícula |
| `V` `W` `r` `l` | la partícula, con su vida avanzada o retrocedida |
| `B` | la partícula, con un valor **sorteado** entre los primeros de su banda (`spawn()`) |
| `P` | el cambio de sitio: la partícula va a esta celda y la que había aquí viene a la suya, las dos escritas a la vez (§7.4) |
| `p` | como `P`, con las celdas de este glifo **compartiendo** el cambio, una parte igual para cada una |

### `x` frente a `o`

Es la diferencia entre **mover** y **crear**:

- `x` (compartido): solo **una** de las celdas se queda la partícula, así que
  nada se duplica. El dado se tira **una vez por regla**, con una cara por celda,
  y se queda la celda que marca: con dos diagonales libres sale al 50 % cada una,
  y con tres o más sigue saliendo a partes iguales. Tirarlo celda a celda no vale:
  la primera ganaría una vez entre *n*, la segunda la misma parte de lo que queda
  y la última se llevaría el resto. Con dos caras eso aún sale mitad y mitad, así
  que el sesgo solo aparece a partir de tres, y por eso estuvo dormido hasta que
  una regla pidió tres destinos (`p`).
- `o` (repetido): cada celda tira **su propio** dado, y pueden ganar varias. Es
  el único glifo que crea de la nada, y por eso solo escribe en celdas vacías.
  La vida que le queda a la partícula **se reparte entre las copias**
  (`lifeOfCopy`): con cuatro `o`, cada hijo se lleva un cuarto de lo que le
  quedaba al padre, y cuando ya no queda nada que repartir **no nace nadie**. Así
  un hijo nunca vive más que su padre.
  En el fuego se usa con cuatro `o` (una de cada cuatro posibilidades por celda):
  media **un** descendiente por generación. Solo con eso estaríamos en el punto
  crítico —ni crece ni se apaga—, pero como cada relevo se lleva un cuarto de la
  vida que queda, la estirpe pierde vida en cada generación y el fuego acaba
  apagándose.

---

## 7. Cómo se aplica una regla

Dos pasos: comprobar (`matches`) y escribir (`applyPattern`).

### 7.1 Comprobar una vecindad

`matches(Step, ...)` recorre el dibujo del `match` y compara glifo a glifo,
**cortando en el primer fallo**. `?` se salta sin comparar.

El ancla se calcula: `anchorX = ancho / 2`, `anchorY = alto / 2`. El centro de un
dibujo es su carácter central, así que **el patrón puede ser de cualquier tamaño
impar** y nadie tiene que decir dónde está el centro. De ahí que convivan reglas
de 1x1 (`M → X`), de 1x3 (`?H? → ?W?`) y de 3x3.

`glyphAt(x, y, cells)` es quien define el mundo:

```java
if (x < 0 || x >= cols || y >= rows) return PARTICLE;   // lados y suelo: sólidos
if (y < 0) return EMPTY;                                // arriba: aire
return Material.glyphOf[cells[y][x]];
```

Eso es **todo** el borde del simulador: dos líneas. Y tiene un efecto bonito: una
llama que intenta subir desde la fila de arriba no encuentra celda donde
escribirse, así que se apaga sola al salir por el techo, sin ningún caso
especial.

### 7.2 Escribir el resultado

`applyPattern` es la parte fina. **Durante el recorrido no escribe la
partícula**: solo decide. Se decide con tres datos:

```java
boolean claimed;            // ¿algún glifo se lleva la partícula?
int destinationX, destinationY, destinationValue;   // dónde y con qué valor
boolean originWritten;      // ¿la regla hizo algo en la celda de la partícula?
```

El valor va separado de las coordenadas porque `V`, `W`, `r`, `l` y `B` **mueven y
cambian el valor a la vez**: el destino no es "la partícula", es "la partícula con
otro valor".

Y al final hay exactamente cinco salidas posibles:

1. **La regla pide un cambio de sitio** → se escriben las dos celdas del cambio
   (§7.4). El cambio manda sobre todo lo demás.
2. **Alguien la reclamó** y el destino está libre → se escribe en el destino. Mover.
3. **Alguien la reclamó** pero otra partícula llegó antes a esa celda → vuelve a
   su sitio. Nunca se pierde nada.
4. **Nadie la reclamó** y la regla no tocó su celda → se queda donde estaba. Es
   el "no pasa nada".
5. **Nadie la reclamó** y la regla escribió otra cosa en su celda (un `_` o una
   letra) → la partícula **se consume**. Esa es la forma de transformar un
   material: `m → F` significa "vacía esta celda de madera agotada y pon fuego".

Si el destino está dentro del dibujo pero fuera de la rejilla, el `continue` lo
ignora: escribir "fuera del mundo" no es un error, simplemente no ocurre.

### 7.3 Qué pasa si dos partículas quieren la misma celda

Las escribe la que llegue primero en el barrido; la segunda se encuentra la celda
ocupada y vuelve a su sitio (salida 2). Por eso el sentido del barrido se
alterna: para que "llegar primero" no signifique siempre lo mismo.

Y desde que una regla puede escribir en la celda de un vecino (§7.5), el sentido
no sólo decide quién gana: `stepParticle` lo recibe (`rightToLeft`) para saber
qué celdas de su propia fila ya ha leído.

### 7.4 El cambio de sitio

`P` y `p` escriben **las dos celdas** del cambio, no solo la suya, cuando el
barrido lee la partícula que lo pide:

1. El grano escribe **su partícula** en la celda de la gota y **la gota** en su
   propia celda. Las dos mitades salen de la misma foto del mundo, así que ninguna
   ve el resultado de la otra.
2. La celda de la gota todavía tiene su turno por delante y su regla escribiría
   encima. No lo toma: **el barrido se salta cualquier celda que ya tenga algo en
   la generación siguiente** (`stepCell`).
3. Si la celda de la gota ya estaba tomada, el cambio no ocurre y el grano se
   queda donde estaba, igual que cualquier otra regla que encuentra su destino
   ocupado.

El punto 2 no necesita nada más, y ahí está la gracia: **una partícula solo se
escribe donde no había nada** (todas las escrituras van con `isFree`, salvo la de
la propia celda y la letra de material que sustituye a la partícula del `match`,
§7.5), así que en las celdas que el barrido todavía no ha leído `nextCells` está
vacía. Si al llegar a una celda hay algo, o estaba vacía en `cells` —y entonces
su turno no hacía nada— o lo escribió una regla que llegó antes: una partícula
que cayó dentro o un cambio, que ya escribió las dos mitades.

Antes eso no se podía saber, así que el cambio se **anotaba**: un array
`exchanges` guardaba las coordenadas del que lo empezaba, y la celda de la gota lo
completaba al llegarle el turno. Era el único estado del motor fuera de `cells` y
`nextCells`, y ya no hace falta: la nota es el propio valor que el cambio deja en
el destino.

El cambio puede **mirar hacia abajo o a su propia fila**, y se valida al cargar la
tabla: hacia arriba no, porque el barrido ya la ha resuelto y lo que hay allí es la
generación siguiente y no la de esta. Hacia abajo la celda no se ha leído nunca; de
lado puede estar leída o no, según el sentido del fotograma, y por eso el cambio pide
lo mismo que la letra de material fuera de su celda (§7.5): si aún no se ha leído,
que esté libre; si ya se leyó, que su partícula siga escrita ahí
(`canGiveItsParticle`). Así, un cambio con una gota que se escurrió en este mismo
paso se cancela en vez de copiar a la que se fue. Un `P` por encima de la fila de la
partícula revienta al arrancar.

La gota que queda suelta **no siempre sube**. Antes de escribirla se mira si la
celda del grano tiene algún **lado libre** en esta generación y en la
siguiente: si lo tiene, la gota sale por ahí y la celda del grano se queda vacía;
si no, sube. Un grano que cae en el agua desplaza una gota, y una gota con sitio
al lado se escurre, no se deja empujar hacia arriba. Sin esa salida el agua
subía por dentro de cualquier columna de una celda de ancho hasta el aire: medido
con el arnés, con la superficie del pozo en la fila 20 de una ventana de 36, la
gota más alta llegaba a la **fila 1**; con la salida llega a la **fila 12**, que
es el nivel que ha subido el pozo con la arena dentro.

Y la gota sube con su valor: el cambio mueve el valor de la celda, no lo dibuja, así
que el pozo conserva la variedad de tonos sin que ninguna regla tenga que sortear
una sombra nueva. Cuando la celda ya estaba leída, el valor que se lleva es el que
ella misma acaba de escribir en la generación siguiente, y no el de esta: una gota
que viene andando su banda no reinicia la espera por cambiarse de sitio.

### 7.5 La letra de material fuera de su celda

Una letra de material (`H`, `A`, `m`…) fuera de la celda de la partícula **no
crea nada: toma el sitio de una partícula que ya está ahí**. Puede ser cualquier
celda del dibujo, también una de su propia fila, y lo que se comprueba depende de
si el barrido ya pasó por ella. Eso lo dice `readYet`, que para la propia fila
necesita el sentido del barrido (`rightToLeft`, §7.3):

- **Celda que el barrido ya leyó** (toda la fila de arriba, y de su fila la parte
  por la que ya ha pasado): se mira `nextCells`. Sólo se escribe si su partícula
  sigue ahí. Se compara por **material** y no por valor, porque la partícula pudo
  escribirse a sí misma con otro tono de su banda (`W`, `r`, `l`, `B`). Si se
  escurrió, o si la ocupó otro material, la regla no escribe: ni crea humo de la
  nada ni borra a nadie.
- **Celda que aún no ha leído** (toda la fila de abajo, y de su fila la parte que
  le queda por delante): `nextCells` está vacía por construcción, así que no dice
  nada; la verdad está en `cells`, que sí tiene la partícula que vio el `match`.
  Se escribe el material y el barrido salta esa celda, igual que cuando un cambio
  escribe las dos mitades (§7.4).

La propia fila estuvo prohibida al principio, porque sin `readYet` era ambigua:
el barrido la recorre en un sentido que se sortea cada fotograma, así que la
mitad de las veces la celda ya estaba leída y la otra mitad no, y el mismo dibujo
daba dos resultados distintos. Con el sentido en la mano la ambigüedad desaparece,
y es lo que permite que el agua de al lado de una llama se convierta en humo.

`_` fuera de su celda sigue la misma regla: sólo vacía a la partícula que el
`match` vio.

---

## 8. Las reglas, por grupos

La tabla `Pattern.STEPS` se lee como una lista de prioridad: **gana la primera
que encaja**. El orden es deliberado y está explicado al final de esta sección.

### Agua contra fuego

Las ocho primeras de la tabla (las llamas que se apagan) y las tres últimas
más cercanas a la gravedad (las gotas que saltan):

| Regla | Dibujo | Qué cuenta |
|---|---|---|
| `FIRE_DIES_*` (ocho, las primeras) | `?A? / ?F? / ???` → `?H? / ?_? / ???` | Una llama con agua en **cualquiera** de sus ocho lados se apaga y deja humo en el sitio de la gota: el vapor del apagado |
| `WATER_PUTS_OUT_ABOVE` | `?F? / ?A? / ???` → `?X? / ?_? / ???` | La gota salta a la celda que la llama acaba de dejar |
| `WATER_PUTS_OUT_UPPER_LEFT` | `F?? / ?A? / ???` → `X?? / ?_? / ???` | Lo mismo cuando la llama está en la diagonal izquierda |
| `WATER_PUTS_OUT_UPPER_RIGHT` | `??F / ?A? / ???` → `??X / ?_? / ???` | Y en la derecha |

Son ocho dibujos para una sola idea porque **un `match` dice qué hay en cada
celda y no sabe decir "agua en cualquier sitio"**: la misma regla hay que verla
desde los ocho lados.

Y es el fuego quien se apaga, no el agua quien lo mata: **una partícula siempre
se escribe a sí misma al final de su paso** (salida 3 de §7.2), así que solo la
llama puede decidir no volver. Si el agua escribiera su celda, el fuego llegaría
después y escribiría encima. Por eso el vapor también lo escribe la llama: es
ella la que decide no volver y, en esa misma regla, deja el humo donde estaba la
gota.

Las ocho formas dejan humo y ninguna inventa una partícula: la gota se convierte
en una bocanada, y no en dos cosas. La celda de la gota puede estar por encima de
la llama (ya leída), a su lado (leída o no, según el sentido del fotograma) o por
debajo (sin leer), y en los tres casos la sustitución es exacta (§7.5): si la gota
ya se había escurrido, la regla no escribe nada y el humo no aparece. Medido con
el arnés: en las ocho direcciones la gota más el humo suman uno, la llama
desaparece y la madera que las rodea no cambia; y tres llamas sobre la misma gota
dejan **un** humo, no tres.

El salto de la gota va **solo hacia arriba**, y no por capricho: el barrido sube
fila a fila, así que la llama de encima ya está apagada cuando se mira el agua y
la celda está libre. Hacia abajo o de lado se movería la gota primero y la llama
la borraría al escribir su propio valor. Los huecos de esos lados no necesitan
regla: el agua los rellena cayendo y fluyendo como cualquier otro.

Y por eso el salto va al final del grupo y no al principio: el centro de esas tres
reglas es `A`, así que solo las mira el agua, y su único requisito es pasar antes
de `FREE_FALL` y de `STAY`. Si fueran después de la gravedad, la gota caería en
vez de saltar al hueco que ha dejado la llama.

### Finales de vida

| Regla | Dibujo | Qué cuenta |
|---|---|---|
| `FIRE_END` | `f → H` | Una llama agotada se convierte en humo, no desaparece |
| `SMOKE_END` | `h → _` | Lo único que se va a la nada es el humo agotado |
| `WOOD_END` | `m → F` | Madera agotada prende: eslabón entre la madera y el fuego |

### Madera

| Regla | Dibujo | Qué cuenta |
|---|---|---|
| `WOOD_BURNS_ABOVE` | `?F? / ?M? / ???` → `?W?` | Madera con fuego encima envejece |
| `WOOD_BURNS_BELOW` | `??? / ?M? / ?F?` → `?W?` | Ídem con fuego debajo |
| `WOOD_BURNS_LEFT` | `??? / FM? / ???` → `?W?` | Ídem con fuego a la izquierda |
| `WOOD_BURNS_RIGHT` | `??? / ?MF / ???` → `?W?` | Ídem con fuego a la derecha |
| `FIRE_FEEDS` | `??? / ?F? / ?M?` → `?l?` | Llama con leña debajo se rejuvenece y se queda quieta |
| `FIRE_FEEDS_ABOVE` | `?M? / ?F? / ???` → `?l?` | Lo mismo con la leña **encima**: la llama también come hacia arriba |
| `WOOD_STAYS` | `M → X` | La madera no cae: es el único material que se queda donde lo pones |

Cuatro reglas para "arder" porque **un patrón solo puede modificar su propia
partícula**: el envejecimiento se escribe desde la madera mirando hacia donde
está el fuego, no desde el fuego hacia la madera.

Las dos reglas de alimentar son simétricas, y no es un detalle: sin
`FIRE_FEEDS_ABOVE` el fuego **sólo comía de abajo**. Una llama encendida bajo un
tronco no tenía entonces leña que comer —arriba estaba la madera, que además le
tapaba el ascenso— ni sitio al que subir, así que se gastaba en el sitio y se
convertía en humo antes de que la madera acabara de arder. Medido con el arnés,
un tronco de 3x4 encendido por debajo no se quemaba; con las dos reglas arde igual
que encendido por arriba (las 12 celdas del tronco pasan por llama y no queda
madera), y la llama sigue subiendo por dentro de la madera como si la hubieran
encendido desde el otro lado.

`WOOD_STAYS` escribe `X` (la partícula) y **no** `M` (el material): con `M` la
madera renacería con la vida a tope en cada generación y no se gastaría nunca.

### Fuego y humo

| Regla | Dibujo | Qué cuenta |
|---|---|---|
| `FIRE_RISES` | `?_? / ?F? / ???` → `?W? / ?_? / ???` | Con hueco encima sube una fila **y envejece** |
| `FIRE_SPREADS` | `??? / ?F? / ???` → `?o? / oWo / ?o?` | Si no puede subir, se propaga a las cuatro celdas de al lado, repartiendo entre ellas la vida que le queda |
| `SMOKE_RISES` | `?_? / ?H? / ???` → `?W? / ?_? / ???` | El humo sube igual, pero sin propagarse |
| `SMOKE_AGES` | `?H? → ?W?` | Humo que no puede subir envejece donde está |

`SMOKE_AGES` parece redundante y no lo es: es la regla que garantiza que **todo
material con vida envejezca aunque esté atrapado**. Sin ella, el humo de un
fuego que ardía bajo la arena no subía, no llegaba nunca a su último valor y se
quedaba para siempre como un grano gris sujetando la arena.

### Agua

| Regla | Dibujo | Qué cuenta |
|---|---|---|
| `WATER_FLOWS_BOTH` | `_A_ / ?X?` → `x_x` | Con hueco en los dos lados, se desliza a uno de ellos |
| `WATER_FLOWS_LEFT` | `_AX / ?X?` → `X_?` | Solo el lado izquierdo libre |
| `WATER_FLOWS_RIGHT` | `XA_ / ?X?` → `?_X` | Solo el lado derecho libre |

Esto es lo que hace que el agua **nivele** en vez de apilarse en pirámide como la
arena. Usa `x` (compartido) y no dos `X`: el agua se reparte, no se duplica. Y
como los lados de la condición son `_`, el destino está garantizado libre.

La fila de abajo solo pide **su propia celda** (`?X?`) y no las tres (`XXX`): lo
de caer ya lo ha resuelto `FREE_FALL`, que va antes, así que cuando se llega aquí
el hueco de debajo no existe; pedir además las dos diagonales solo servía para
bloquear la nivelación en cuanto el lecho tenía una irregularidad, que es justo
donde un líquido más tiene que nivelar.

Van **después** de `FREE_FALL` (si hay hueco justo debajo, la gota cae) y
**antes** de los deslizamientos diagonales: un líquido no tiene ángulo de
reposo, así que una gota que ha aterrizado se abre de lado y solo rueda por una
pendiente cuando ya no le queda hueco lateral. Con las diagonales primero se
apila en el mismo cono que la arena.

Y **antes** de `STAY` (si fueran después, el agua se apilaría y nunca llegaría a
ellas).

### Ácido

Veinte reglas en dos sitios: el final de la carga va arriba, con el apagado del
fuego; todo lo demás va después de la caída, en el orden de la vida de la gota
—hundirse, morder, rodar, abrirse de lado y sólo entonces diluirse— y antes de las
diagonales. Las tres nivelaciones de la gravedad van con las del agua; las tres de
dentro del agua van con los rodamientos. El paso del grano está en la tabla porque
es del arenal, no del ácido.

| Regla | Dibujo | Qué cuenta |
|---|---|---|
| `ACID_END` | `c → A` | La gota que llegó al final de su banda queda agua: se ha neutralizado, la haya gastado el agua o la madera |
| `ACID_SINKS_SIDEWAYS_IN_WATER` | `??? / ?C? / AAA` → `??? / ??? / ppp` | Con agua en las tres celdas de debajo, la gota baja a una de ellas, sorteadas: se hunde sin cortar el pozo en columnas |
| `ACID_SINKS_IN_WATER` | `??? / ?C? / ?A?` → `??? / ??? / ?P?` | El ácido pesa más que el agua: cambia de sitio con la gota de debajo y baja una fila |
| `ACID_CORRODES_BELOW` | `??? / ?C? / ?M?` → `??? / ?r? / ?H?` | Madera debajo: la madera deja humo y la gota paga el bocado en el acto |
| `ACID_CORRODES_ABOVE` | `?M? / ?C? / ???` → `?H? / ?r? / ???` | Madera encima, lo mismo |
| `ACID_CORRODES_LEFT` | `??? / MC? / ???` → `??? / Hr? / ???` | Madera a la izquierda |
| `ACID_CORRODES_RIGHT` | `??? / ?CM / ???` → `??? / ?rH / ???` | Madera a la derecha |
| `ACID_ROLLS_SIDEWAYS_IN_WATER` | `??? / ?C? / ACA` → `??? / ??? / p?p` | Ácido sobre ácido con agua en las dos diagonales: cambia de sitio con una de ellas, sorteadas |
| `ACID_ROLLS_LEFT_IN_WATER` | `??? / ?C? / ACX` → `??? / ??? / p??` | Lo mismo con agua sólo en la diagonal izquierda |
| `ACID_ROLLS_RIGHT_IN_WATER` | `??? / ?C? / XCA` → `??? / ??? / ??p` | Lo mismo con agua sólo en la diagonal derecha |
| `ACID_LEVELS_SIDEWAYS_IN_WATER` | `??? / ACA / ?C?` → `??? / p?p / ???` | Ácido sobre ácido con agua en los dos lados: cambia de sitio con una de ellas, sorteadas, y se queda en su fila |
| `ACID_LEVELS_LEFT_IN_WATER` | `??? / ACX / ?C?` → `??? / p?? / ???` | Lo mismo con agua sólo en el lado izquierdo |
| `ACID_LEVELS_RIGHT_IN_WATER` | `??? / XCA / ?C?` → `??? / ??p / ???` | Lo mismo con agua sólo en el lado derecho |
| `ACID_DILUTES_ABOVE` | `?A? / ?C? / ???` → `??? / ?W? / ???` | Agua encima: la gota paga un valor de su carga, pero sólo cuando le toca la vejez |
| `ACID_DILUTES_BELOW` | `??? / ?C? / ?A?` → `??? / ?W? / ???` | Agua debajo, lo mismo |
| `ACID_DILUTES_LEFT` | `??? / AC? / ???` → `??? / ?W? / ???` | Agua a la izquierda |
| `ACID_DILUTES_RIGHT` | `??? / ?CA / ???` → `??? / ?W? / ???` | Agua a la derecha |
| `ACID_FLOWS_BOTH` | `_C_ / ?X?` → `x_x` | Con hueco en los dos lados, se desliza a uno |
| `ACID_FLOWS_LEFT` | `_CX / ?X?` → `X_?` | Solo el lado izquierdo libre |
| `ACID_FLOWS_RIGHT` | `XC_ / ?X?` → `?_X` | Solo el lado derecho libre |
| `SAND_FALLS_THROUGH_ACID` | `??? / ?S? / ?C?` → `??? / ??? / ?P?` | El grano cambia de sitio con la gota en vez de quedarse encima |

Caer, deslizarse por la diagonal, apilarse y el reloj no necesitan nada: el
ácido es un grano de arena con reglas propias de líquido, igual que el agua. De
ahí que las tres nivelaciones sean copia literal de las del agua y que la
corrosión no tenga que copiarse dieciséis veces: el `X` de la gravedad ya vale
para cualquier material.

**Dos velocidades con una sola banda.** Las diluciones escriben `W` y las
corrosiones escriben `r`, y esa es toda la diferencia entre las dos reacciones:
`r` cobra en el turno en que ocurre, porque morder es un suceso; `W` sólo cobra
cuando la vejez le da el turno, porque disolverse es una espera. La misma banda
de seis valores da así dos ritmos: una gota que muerde se gasta en cinco turnos y
una gota que el agua come se gasta en seiscientos pasos, uno cada sesenta turnos
—ciento veinte pasos, porque su reloj es 2—. La vejez 60 del ácido está puesta
para lo segundo; la corrosión no la mira siquiera.

**Neutralizar es un proceso y no un paso.** El primer intento fue un cambio de
celda —`?A? / ?C?` → `?A?`—, y con eso el frente avanzaba una celda por paso,
nueve por fotograma: un charco se hacía agua en un par de fotogramas desde la
primera gota que cayera dentro. Ahora el agua no transforma la gota, le come la
carga. Cada valor cuesta sesenta turnos —ciento veinte pasos—, así que el frente
avanza una celda cada seiscientos pasos, que es lo que necesita un charco para
sobrevivir no a una gota, sino a la partida entera. Medido con el arnés, treinta
de ácido en el fondo de un pozo de 570 agua: los primeros valores empiezan a
pagarse hacia el paso 480, la capa de fuera hacia el 600 y el charco entero es
agua entre el 1.200 y el 1.800. Y la cuenta sale: 570 + 30 = 600 gotas, cada
ácido gastado deja su agua.

**El agua no es un muro.** Las diluciones fueron primero por encima de las
corrosiones, y eso parecía buena idea —el agua gana, se lo come antes de que
muerda— hasta que se miró qué hace un ácido echado sobre un pozo con madera en el
fondo: `?A? / ?C?` encaja con cualquier ácido que toque agua, así que la gota
dejaba de morder en cuanto entraba en el agua y la madera no se enteraba. Ahora
el orden es el de la vida de la gota: primero se hunde (una fila cada dos pasos,
`P` o `p`), luego muerde lo que tenga al lado y sólo cuando no puede hacer
ninguna de las dos se deja diluir. Medido con el arnés, 150 de ácido sobre 300 de
agua y un tablón de 300 de madera: nada comido al paso 20 —el charco todavía está
bajando—, 42 celdas al 120, 111 al 600, 131 al 1.200 y 135 al 2.400. El agua
frena la corrosión, pero no la impide.

**La gota baja sin cortar el agua en columnas.** El hundimiento se dice de dos
maneras, y la primera es la que se ve. Con agua en las **tres** celdas de
debajo, la gota baja a una de ellas sorteadas a partes iguales (`ppp`), que es el
mismo dibujo que usa el grano de arena para hundirse; con las diagonales secas
baja recta. Sólo con la recta, una capa de ácido sobre un pozo baja tal cual,
cada gota en su columna de una celda, y lo que se ve es un hilo de coser y no un
líquido. Con el sorteo la gota se abre al bajar y llega al fondo repartida, como
hace un líquido más pesado que el agua de verdad. No cuesta nada: los tres
destinos son la fila que el barrido aún no ha leído y el cambio sube **una** gota
de agua, ni una más.

Medido con el arnés: veinte gotas en una capa de diez columnas sobre un pozo.
Las gotas llegan al fondo en unos 110 pasos —una fila cada dos pasos, sin perder
ninguna— y allí ocupan **27 columnas** de ancho, en vez de las diez de la capa de
partida. La cuenta no se mueve (3.082 partículas antes y después). Sin el sorteo
la capa baja en diez columnas de una celda, que es el hilo de coser que se quería
evitar.

Y cuando las diagonales no son agua la regla no encaja y la gota baja recta, que es
lo que impide que un ácido que cae dentro de un pozo por la superficie se escape de
lado en cuanto toca el agua.

**El charco se abre también en el fondo.** Lo de arriba resuelve la gota que cae;
lo que se apila es otro problema. Sobre agua la gota se hunde; sobre otra gota de
ácido no había nada que hacer, porque las nivelaciones piden hueco libre a los
lados y dentro del agua lo que hay es agua: la gota que aterrizaba encima se
quedaba quieta y el charco salía en columna. Ahora, si lo de abajo es ácido y hay
agua en una diagonal, los dos cambian de sitio: el ácido baja a la diagonal y la
gota de agua sube a la celda que deja. Es el dibujo de los deslizamientos de la
gravedad leído del revés —`p` en vez de moverse a un hueco, y con el agua de
pago—, y con las dos diagonales de agua el destino se sortea (`p?p`).

Medido con el arnés, ochenta de ácido vertidos en una columna de cuatro por veinte
dentro de un pozo de 2.924 de agua: a los 120 pasos ocupan **24 columnas** de
ancho y **7 filas** de alto; sin la regla, 16 de ancho y 20 de alto, que es la
columna. El charco se abre antes de diluirse, que es lo que se buscaba.

Van después de las corrosiones —una gota que puede morder, muerde— y, sobre todo,
**antes** de las diluciones: la dilución reclama la celda de la gota incluso en
los turnos en que la vejez no le toca, así que con el otro orden un ácido rodeado
de agua se quedaba en su sitio para siempre y el charco no se abría nunca.

**Y el charco se aplana, que es lo que hace un líquido.** El rodar de arriba abre
la pila, pero no la nivela: la gota baja una fila y una columna, así que el borde
de cada fila tiene ácido justo en la diagonal de debajo y el monte se queda en
**cuarenta y cinco grados**, como un montón de arena. La nivelación del agua no
puede hacerlo: está escrita con `x` y sólo escribe donde hay hueco libre, y dentro
del pozo lo que hay a los lados es agua. Lo que sí se puede es **cambiarse de
sitio con esa agua**: la gota se va al lado y la gota desplazada ocupa la celda
que deja. No es un `x` con otro relleno, es el mismo cambio de sitio del
hundimiento leído de lado, y por eso el `p` va en su propia fila: eso es lo que
obligó a que un cambio pudiera mirar de lado (§7.4).

La gota que se desliza no baja, se queda en su fila, y la lámina crece hasta el
ancho del pozo. Lo que la baja es lo de siempre: cuando sale del pie de la fila de
debajo, lo que tiene debajo es agua y el hundimiento se la lleva; el agua que deja
tras de sí es el hueco que acaba de dejar libre. Por eso la regla **pide ácido
debajo**: la gota que tiene agua debajo se hunde, que es lo que va antes, y la
última fila del charco no encaja nunca, porque lo que tiene debajo es el suelo del
pozo. Sin esa condición, cualquier gota del fondo se pasearía de lado por el agua
sin bajar jamás.

Medido con el arnés, cuarenta gotas en una columna sobre un pozo de 100x45 con la
superficie en la fila 30: al paso 600 ocupan **37 columnas de ancho y 2 filas de
alto**, frente a las 28 y las 4 sin la nivelación; al 900 ya es **una sola fila**
de 37 columnas, donde el cono sigue con 10 de ancho y 3 de alto. Las 4.540
partículas de la escena están iguales al principio y en cada medida —pasos 200,
400, 600, 900, 1.200 y 1.600—, así que el cambio no crea ni pierde nada.

**Una lámina se diluye antes que un cono**, y no es un fallo de las reglas: en el
cono el interior no toca el agua y no paga, y en la lámina cada gota tiene agua
arriba y al lado. Medido, al paso 1.200 quedan 13 gotas en una fila y al 1.600
ninguna, frente a las 18 y las 8 del cono. Es lo mismo que el agua le hace a un
charco de verdad, que se come la superficie; si se quiere que la lámina dure, lo
que se toca es la vejez del ácido y no la nivelación.

**Por qué ahora sí.** Ese mismo final se probó con la neutralización de un paso y
destruyó el material: el agua de cada bocado tocaba el ácido de al lado y se
llevaba el charco entero, así que un chorro de doscientas gotas comía una celda.
Con la carga cobrada el final es **una** gota y el agua que deja tiene que
comerse la siguiente, de modo que el charco sobrevive y se disuelve desde la
superficie. Medido: el chorro de doscientas gotas sobre un tronco de 440, con
veinte filas de agua por medio, come 38 celdas en 400 pasos y 122 en 2.400; a los
4.800 no queda una gota de ácido y todo el chorro es agua. El ácido se autolimita,
pero come.

**Quien paga es quien escribe.** Las corrosiones y las diluciones están centradas
en el ácido y no en la madera ni en el agua, y eso no es un capricho de estilo:
los glifos que mueven una vida (`r`, `W`, `l`…) siempre escriben la partícula que
se está barriendo, así que un `W` puesto en la celda de un vecino **mueve al
vecino** en vez de gastarlo. Una regla centrada en la madera con el `r` en la
celda del ácido llevaría la madera quemada a la celda del ácido y el ácido
desaparecería, sin gastar nada. Y el humo lo escribe el ácido con una letra de
material fuera de su celda, que toma el sitio de la partícula que ya está ahí
(§7.5): el mismo mecanismo que el vapor del apagado.

**El final es agua.** El ácido que no tiene carga queda agua, y da igual de qué
lado venga el gasto: si lo gastó el agua, la neutralización está hecha; si lo
gastó la madera, la gota ha terminado de morder y lo que deja es un líquido
neutro. El agua nace con `spawn()` en la celda de la gota y el agua que estaba
comiendo no se toca: escribir `A` en su celda la reiniciaría y soltaría al grano
que tuviera encima esperando (§7.5).

**El humo es lo que limita la hondura.** El humo de un bocado nace en la celda de
la madera y ahí abajo no tiene por dónde subir, porque encima está el ácido: se
queda en su celda hasta que se gasta (104 pasos) y las gotas de arriba no pueden
comer lo que él ocupa. Medido con el arnés, el chorro llega como mucho a la fila
47 de un tronco que empieza en la 40, y el ácido que sigue vivo es el que aún no
ha llegado a la superficie. No hace falta ninguna regla de freno: el gas atrapado
ya es el freno.

**Dos gotas sobre la misma celda.** Cuando dos gotas ven la misma celda de madera
en el mismo paso sólo se escribe un humo y las dos pagan el bocado: la cuenta de
partículas se mantiene, la carga de esa esquina se pierde. Es la única forma de
perder algo en esta reacción, y es lo que hace que un charco grande rinda menos
que la misma cantidad de ácido en gotas separadas.

### La gota que sujeta un grano: el hundimiento lento

Cuando un grano cae sobre agua no se hunde: se queda encima, y la gota que lo
sostiene empieza a **envejecer**. Cuando llega al final de su banda, los dos
**cambian de sitio**: el grano baja una fila y la gota sube una. No hay ningún
material nuevo: la espera es la posición de la gota dentro de su banda (§5), y por
eso el tramo de espera de esa banda está pintado de un solo color.

| Regla | Dibujo | Qué cuenta |
|---|---|---|
| `WATER_WAITS_UNDER_SAND` | `?S? / ?A? / ???` → `??? / ?W? / ???` | La gota que tiene un grano encima avanza un paso en su banda y no se va a otro sitio: la espera no se gasta cayendo ni nivelando |
| `WATER_FORGETS_THE_WAIT` | `?a?` → `?A?` | Una gota que llegó al final **sin** grano encima vuelve a nacer: la arena se ha ido y no hay con quién cambiar |
| `SAND_SINKS_SIDEWAYS_INTO_WATER` | `??? / ?S? / AaA` → `??? / ??? / ppp` | Con la gota de debajo al final de su banda y las dos diagonales también agua, el grano baja a una de las tres, sorteadas |
| `SAND_SINKS_STRAIGHT_INTO_WATER` | `??? / ?S? / ?a?` → `??? / ??? / ?P?` | Lo mismo cuando las diagonales no son agua: el grano baja recto, sin elegir |
| `SAND_ROLLS_SIDEWAYS_IN_WATER` | `??? / ?S? / ASA` → `??? / ??? / p?p` | Grano sobre grano con agua en las dos diagonales: cambia de sitio con una de ellas, sorteadas |
| `SAND_ROLLS_LEFT_IN_WATER` | `??? / ?S? / ASX` → `??? / ??? / p??` | Lo mismo con agua sólo en la diagonal izquierda |
| `SAND_ROLLS_RIGHT_IN_WATER` | `??? / ?S? / XSA` → `??? / ??? / ??p` | Lo mismo con agua sólo en la diagonal derecha |

En `AaA` el `a` del centro es la gota **que el grano tiene debajo**, la única que
la espera ha mirado; las de las diagonales son agua cualquiera. Y `ppp` es el
cambio de sitio repartido entre las tres (§7.4): el grano baja a una, sorteadas a
partes iguales, y **una sola** gota sube. La de subir no se sortea con `x` porque
el grano y la gota no cambian de valor, solo de celda: el cambio escribe las dos
mitades y el barrido se salta la celda de la gota (§7.4).

Los dos hundimientos van **después** de la espera y de la de olvidar: la espera
deja la gota al final de su banda un paso antes, y el olvido reinicia la que ya no
tiene grano. Y las cuatro van antes de `FREE_FALL`, porque la gota que espera
sigue siendo agua y, sin grano encima, vuelve a nacer antes de que le toque
moverse. La de esperar va antes que la de olvidar, que encaja con cualquier gota
del final de su banda y reiniciaría la que todavía tiene un grano encima. Los tres
rodamientos van detrás de los dos hundimientos y antes de `FREE_FALL` también,
porque son para el grano que ya tiene arena debajo.

**El ritmo.** Cada fila cuesta lo que tarda la gota de debajo en llegar al final
de su banda: unos 57 pasos, y el grano la mira una vez cada 12, así que cada fila
son **60 pasos exactos**, 6,7 fotogramas con el multiplicador en 9, frente al
fotograma y un tercio del aire. Medido con el arnés, un grano sobre un pozo de
agua: fila 1 en el paso 1, fila 2 en el 61, fila 3 en el 121... fila 15 en el 841,
sesenta por fila.
Y la cuenta de partículas se conserva: los granos que entran son los que salen y
las gotas que entran son las que salen.

**La gota que sube no sube sola.** Lo que el grano deja al caer es una gota
desplazada, y una gota con un lado libre se **escurre** por ahí en vez de subir
(§7.4): una columna de una celda de ancho tiene los dos lados libres en el aire,
así que el agua no puede trepar por ella. Sin esa salida, cualquier chorro de
arena subía el agua por dentro hasta arriba del todo. Medido con el arnés, con la
superficie del pozo en la fila 20 de una ventana de 36 y un chorro de 101 granos:
la gota más alta llegaba a la **fila 1** antes y a la **fila 12** después, que es
el nivel que sube el pozo con la arena dentro. Dentro del agua no hay lados
libres, así que la gota sube igual y el hundimiento no cambia.

**El grano también rueda cuando lo de abajo es arena.** El hundimiento de más
arriba es para el grano que aterriza sobre agua; el que aterriza sobre otro grano
no tenía nada. `SLIDE_BOTH`, `SLIDE_LEFT` y `SLIDE_RIGHT` piden hueco libre en la
diagonal, y dentro del agua lo que hay es agua, así que las tres fallaban y la
tabla caía en `STAY`: una columna de arena dentro de un pozo crecía como una
columna. Las tres reglas nuevas son esos mismos dibujos leídos del agua: si la
diagonal es una gota, grano y gota cambian de sitio y el grano baja una fila. Con
las dos diagonales de agua el destino se sortea; con una sola no hay nada que
sortear, y si la diagonal es un hueco siguen ganando los deslizamientos de
siempre, que van detrás y no gastan agua. Van después de los dos hundimientos —el
grano que tiene agua justo debajo se hunde, no rueda— y antes de `FREE_FALL`.

Medido con el arnés, ochenta granos vertidos en una columna de cuatro por veinte
dentro de un pozo de 2.924 de agua, a los 2.400 pasos: **entre 29 y 39 columnas
de ancho y 4 o 5 filas de alto**, frente a las 10 de ancho y las 22 de alto de
antes. La cuenta no se mueve (el pico son las mismas 3.082 partículas del
principio) y la espera sigue costando lo que costaba: un grano solo sobre el pozo
baja una fila cada 60 pasos, sin que el rodar la reinicie. La arena al aire no se
entera: la misma columna sobre suelo seco acaba en 19 de ancho y 8 de alto con
las reglas y sin ellas, porque sin agua en la diagonal no encajan.

**Por qué el grano no se mueve de lado mientras baja.** Un cambio lateral pone a
las dos partículas en la misma fila, y el sentido de esa fila se sortea cada
fotograma: en los fotogramas en que la gota se lee primero, ella resuelve su celda,
el cambio se encuentra el destino tomado y se cancela; en los otros el cambio
escribe las dos mitades y el barrido se salta la gota. El grano se hundiría a
trompicones, un sí y un no alternos, y lo que pasara dependería de la paridad del
fotograma. El cambio vertical no tiene ese problema: las dos partículas están en
filas distintas y el barrido las mira siempre en el mismo orden, la de arriba
primero (§7.4).

### Gravedad (sirve para todos los materiales)

| Regla | Dibujo | Qué cuenta |
|---|---|---|
| `FREE_FALL` | `??? / ?X? / ?_?` → `?X?` abajo | Cae una fila |
| `SLIDE_BOTH` | `??? / ?X? / _X_` → `x?x` | Algo debajo, las dos diagonales libres: una de las dos |
| `SLIDE_LEFT` | `??? / ?X? / _XX` → `X??` | Solo la diagonal izquierda |
| `SLIDE_RIGHT` | `??? / ?X? / XX_` → `??X` | Solo la diagonal derecha |
| `STAY` | `??? / ?X? / XXX` → `?X?` | Nada libre: se apila |

El centro es `X`, así que estas reglas valen para **cualquier** partícula. La
madera y el fuego se libran de ellas por prioridad, no por una excepción del
motor.

### Por qué ese orden

1. **El agua apagando el fuego antes que todo**: una llama que toca agua nunca
   llega a subir ni a propagarse, así que el agua es un muro de verdad y no un
   obstáculo que la llama rodea. El salto de la gota no necesita ese privilegio
   y va justo delante de la gravedad, donde solo le estorba caer.
2. **Los finales de vida después**: un `m` con llama al lado debe arder, no
   envejecer; y una madera agotada no debe moverse antes de convertirse.
3. **Madera antes que fuego**: el combustible decide antes de que la llama suba
   o se propague.
4. **El comportamiento del material antes que la gravedad**: el agua tiene que
   nivelar de lado *antes* de llegar a `STAY`, y el fuego subir antes de caer.
   Y dentro de la gravedad la nivelación va **antes** que los deslizamientos
   diagonales: un líquido no tiene ángulo de reposo, así que una gota que ha
   aterrizado se abre de lado y solo rueda por una pendiente cuando ya no le
   queda hueco lateral. Con las diagonales primero se apila en el mismo cono
   que la arena.
5. **La gravedad al final, como red**: cualquier material que no diga otra cosa
   se comporta como un grano de arena.
6. **Las cuatro reglas del hundimiento van juntas y antes de la gravedad**, porque
   lo que sostiene al grano es la gota, que sigue siendo agua, y su banda es la
   espera. Dentro del grupo el orden no es libre: la espera y el olvido van antes
   que los dos hundimientos, porque son los que llevan la gota al final de su banda
   (y el olvido reinicia la que ya no tiene grano encima, que encaja con cualquier
   gota del final). Y los hundimientos van antes de `FREE_FALL`: un grano no cae
   **al** agua, se cambia con ella. Los tres rodamientos van justo detrás, el del
   grano y los dos del ácido: son para la pila y no para la caída, así que ninguno
   de los dos hundimientos puede encajar antes, y un hueco en la diagonal sigue
   ganando al agua porque los deslizamientos de la gravedad van detrás.
7. **El ácido aparece en dos sitios, y los dos son deliberados.** Su final va
   arriba con el apagado: el ácido gastado se hace agua antes de que nadie le pida
   nada más, y va **antes** que las corrosiones y que las diluciones, porque `r` y
   `W` no pueden pasar del final de la banda y un ácido gastado que llegara a
   ellas no se haría agua nunca. Todo lo demás va después de la caída y antes de
   las diagonales, y en el orden de la vida de la gota: primero se **hunde**
   (`ACID_SINKS_SIDEWAYS_IN_WATER` y `ACID_SINKS_IN_WATER`, dos cambios de sitio
   que solo pueden ir después de `FREE_FALL`: si debajo hay un hueco, la gota cae;
   si hay agua, se la cambia, y con agua en las tres celdas de debajo se sortea a
   cuál de ellas), luego **muerde** lo que tenga al lado, después **rueda** si lo
   que tiene debajo es más ácido (`ACID_ROLLS_*`, el mismo cambio de sitio que el
   hundimiento pero hacia la diagonal), en seguida **se abre de lado** si lo que
   tiene al lado es agua (`ACID_LEVELS_*`, el mismo cambio pero en su propia fila,
   que es lo que aplana el charco) y solo cuando no puede hacer ninguna de las
   cuatro se deja **diluir**. Con las diluciones delante, una gota que toca agua y
   madera a la vez no muerde: la diluyen primero, y eso convertía cada gota de
   agua en un muro entre el ácido y la madera. Detrás, el agua frena la corrosión
   pero no la impide. Y el rodar y el abrirse de lado tienen que ir delante de las
   diluciones por un motivo que no se ve en el dibujo: la dilución reclama la
   celda de la gota aunque la vejez no le toque, así que puesta antes dejaría
   quieto para siempre a cualquier ácido rodeado de agua.

---

## 9. La paleta y el dibujo

- 256 colores; el 0 es blanco (fondo).
- Cada banda se interpola entre dos y ocho paradas de color (`Material.color`).
  La rampa se escribe en la lista de materiales, no en el código de dibujo. Las
  paradas de más sirven para colocar un cambio de color en un punto exacto de la
  banda: el agua usa ocho, una por cada ocho valores, para que el primer segmento
  acabe justo donde termina el sorteo de nacimiento y el resto de la banda sea un
  solo color.
- El último valor de un material con vida se interpola un 85 % hacia blanco: el
  fantasma.
- El dibujo escribe bloques de 8x8 píxeles en un `byte[]` en orden BGRA y lo
  manda a la imagen con una sola llamada (`PixelWriter.setPixels`). Sin
  asignaciones por celda: el bucle caliente no crea objetos.

---

## 10. Añadir cosas

### Un material nuevo

Una línea en `Material.LIST`:

```java
new Material('P', 40, true, 5, 3, 1, "#ffffff", "#888888"),
```

Glifo, tamaño de la banda, si tiene vida, en cuántos valores puede nacer, **cada
cuántos pasos del mundo da un paso**, **cuántos de esos turnos tarda en gastar un
valor** y las paradas de color. La banda, la tabla
inversa, el botón del selector y la tecla salen solos. Si no se porta de forma
especial, cae por gravedad como la arena. El reloj es lo que lo hace rápido o
lento **frente a los demás materiales**: 1 es un paso en cada paso, 9 es uno de
cada nueve y 22 es hoy el más lento, el de la madera y el fuego. La vejez es lo que
lo hace durar más de lo que mide su banda: con 1 la vida es el ancho de la banda, y
con 13 una banda de 8 valores sube 79 filas. Para acelerar o frenar el
mundo entero no se toca el reloj, se toca `ENGINE_MULTIPLIER`, y lo único que hay
que cuidar es que los turnos del material no caigan siempre en fotogramas del mismo
lado (ver "El reloj de cada material") y que `ENGINE_MULTIPLIER × FRAMES_PER_PAINT`
sea impar, o el chorro del agua se queda congelado en el aire (§3).

Ningún material de hoy está fuera del pincel: todas las bandas son materiales del
catálogo. La versión anterior tenía una excepción, la marca del agua que sujetaba un
grano, declarada con un `false` antes de los colores para que no saliera en el
selector ni en las teclas; al convertir esa espera en un tramo de la banda del agua
desapareció la excepción y el constructor tiene ya una sola forma.

### Una regla nueva

Un `Step` en `MaterialRules.Pattern` y su sitio en `STEPS`:

```java
private static final Step MI_REGLA = new Step(
        new String[] { "???", "?M?", "???" },
        new String[] { "???", "?W?", "???" });
```

Hay que respetar tres cosas:

1. `match` y `result` con la **misma forma** y tamaño **impar** (se valida al
   cargar, así que un error revienta al arrancar).
2. El carácter central del `match` tiene que encajar con la partícula que estás
   describiendo; el del `result` es la celda de la que sale.
3. **Todo material con vida necesita una regla que lo envejezca aunque no pueda
   moverse.** Si la única regla que lleva `W` pide hueco libre al lado, el
   material atrapado será inmortal.

### Un glifo nuevo de resultado

Un `case` en el `switch` de `applyPattern` y, si necesita dados, su cuenta en
`Step` (como `shared` y `copies`, para no contarla en cada partícula).

---

## 11. Compilar, ejecutar, comprobar

En este equipo (Windows, JDK 17 y JavaFX 21 por ruta absoluta, porque el `javac`
del PATH es otro):

```powershell
& "C:\Program Files\Java\jdk-17.0.20.1\bin\javac.exe" -Xlint:all `
  --module-path "C:\Program Files\Java\javafx-sdk-21.0.8\lib" `
  --add-modules javafx.controls,javafx.graphics -d bin src/principal/*.java src/sand/*.java

& "C:\Program Files\Java\jdk-17.0.20.1\bin\java.exe" `
  --module-path "C:\Program Files\Java\javafx-sdk-21.0.8\lib" `
  --add-modules javafx.controls,javafx.graphics -cp bin principal.MaterialSimulator
```

Para abrir el motor original, `-cp bin sand.Sand`.

Y para revisar la documentación:

```powershell
& "C:\Program Files\Java\jdk-17.0.20.1\bin\javadoc.exe" -Xdoclint:all,-missing `
  --module-path "C:\Program Files\Java\javafx-sdk-21.0.8\lib" `
  --add-modules javafx.controls,javafx.graphics -d docs src/principal/*.java src/sand/*.java
```

### Verificar sin abrir la ventana

Para tocar reglas, lo más rentable es un arnés temporal en el paquete
`principal`: una clase con su propia rejilla, su doble búfer y el mismo barrido
alternado, que llama a `stepParticle` en bucle y cuenta partículas por material.
Se compila junto con las fuentes y se borra al terminar.

Comprobaciones que han demostrado ser útiles (todas encontraron algo alguna vez):

| Comprobación | Qué vigila |
|---|---|
| Conservación de la arena | 100 granos entran, 100 granos salen |
| Centroide del montón | No se ladea (la alternancia del barrido) |
| La madera no cae | `WOOD_STAYS` |
| El fuego se apaga | Un fuego suelto no es inmortal |
| El fuego sube un tercio de pantalla | El ancho por la vejez (10 × 3 = 30 filas) |
| El humo llega al techo | El ancho por la vejez (8 × 13 = 79 filas) |
| El agua apaga el fuego | Las ocho reglas de apagado |
| El agua no se pierde al apagar | 25 gotas entran, 25 gotas salen |
| Un fuego no cruza una mancha de agua | Que la llama no rodee la gota |
| Un bloque de madera se quema entero | Que el fuego no se limite a una columna |
| Quemar bajo la arena no deja residuo | Que todo material con vida envejezca atrapado |
| El agua se nivela | Las reglas laterales |
| Subir el multiplicador no cambia la forma | La arena con reloj 12 deja el mismo montón (centroide −0,14, alto 17, ancho 37) con el multiplicador en 4 que con 2 |
| La arena va a un doceavo | 73 filas de caída en 217 fotogramas, una cada 3 fotogramas (reloj 12 con 4 pasos por fotograma) |
| El agua cae doce veces más rápido | Las mismas 73 filas en 19 fotogramas, frente a los 217 de la arena |
| El reloj no pierde partículas | 330 granos entran, 330 salen; ídem con las gotas |
| El reloj no ladea las pilas | Bloque de 11x30 con reloj 12 y multiplicador 4: centroide a 0,14 celdas del centro, 17 de alto y 37 de ancho; con el cociente par (12 y multiplicador 2) se va 5,4 y se aplana (13 y 42) |
| El fuego y el humo suben lo mismo | 25 filas la llama y el humo hasta la fila 0, medido con las bandas comprimidas (10 y 8 valores) |
| Comprimir una banda no cambia la física | Tronco de 12 celdas quemado entero desde arriba (4203 pasos) y desde abajo (2927), con M=0 en los dos; conservación de arena y agua a 720 pasos; el grano se hunde en 301 pasos |
| El agua se aplana y la arena no | Cien gotas vertidas en un punto: lámina de 2 de alto en 100 columnas, desnivel 1; la misma arena deja un cono de 8 |
| El chorro sale recto | Con un grano cada dos turnos del material, el agua y la arena caen en una columna de 1 celda de ancho; con uno por turno, en 3 |
| La arena se hunde sin marca | Nueve granos sobre un charco de 108 gotas: 60,08 pasos por fila (6,7 fotogramas frente a 1,3 en el aire), arena 9 → 9, gotas 108 → 108 y ninguna gota atrapada en el tramo de espera |
| El fotograma cabe de sobra | Pintado 0,95 ms y los pasos del mundo 0,11 ms cada uno (0,45 ms los cuatro, 0,9 los ocho, medidos con el multiplicador en 8), de los 16,6 ms que hay a 60 fps (100x75 celdas, 3750 granos) |
| La madera deja humo | Cada celda comida deja un humo, no aparece ni una gota, y las 160 partículas de la escena siguen siendo 160 |
| Neutralizar es un proceso | Treinta de ácido en el fondo de un pozo de 570 de agua: los primeros valores se pagan hacia el paso 480, la capa de fuera hacia el 600 y todo el charco es agua entre el 1.200 y el 1.800, con 570 + 30 = 600 gotas |
| El ácido va a la mitad | Una gota sobre un pozo: fila 21 al paso 10, 26 al 20, 31 al 30 y 36 al 40, o sea una fila cada dos pasos, frente a una por paso con el reloj en 1 |
| El reloj del ácido no toca la química | Con el reloj en 2 y la vejez en 60 la dilución termina igual (el charco de 30 en un pozo de 570 es agua entre el 1.200 y el 1.800) y la madera comida al 2.400 es 226 frente a 227 con el reloj en 1 |
| Un chorro de ácido se autolimita | Doscientas gotas sobre un tronco de 440 celdas, con veinte filas de agua por medio: 38 comidas en 400 pasos y 122 en 2.400, y a los 4.800 no queda una gota de ácido; el pico de ocupación es el total del principio |
| El ácido se hunde en el agua | Una gota sobre un pozo baja una fila cada dos pasos (fila 21 al paso 10, fila 36 al 40) y el agua no se entera: el pico de ocupación es el total del principio |
| El ácido baja sin cortar el agua en columnas | Veinte gotas en una capa de diez columnas sobre un pozo: llegan al fondo repartidas en 27 columnas de ancho al paso 120, sin ladeo y sin perder una partícula (el pico son las 3.082 del principio) |
| El ácido atraviesa el agua y come la madera | 150 de ácido sobre 300 de agua y un tablón de 300: nada comido al paso 20, porque el charco todavía está bajando, 42 al 120, 111 al 600, 131 al 1.200 y 135 al 2.400 |
| La arena se hunde en el ácido | El grano llega a la fila 59 de 60 de un pozo de 600 y el ácido no se gasta (600 → 600) |
| La arena se abre en cono dentro del agua | Ochenta granos en una columna de cuatro por veinte, en un pozo de 2.924 de agua: entre 29 y 39 columnas de ancho y 4 o 5 filas de alto al paso 2.400, frente a las 10 de ancho y las 22 de alto sin el rodar; el pico son las mismas 3.082 partículas del principio |
| El ácido se abre en el fondo | Las mismas ochenta gotas en el mismo pozo: 24 columnas de ancho y 7 de alto al paso 120, frente a 16 de ancho y 20 de alto sin el rodar |
| El ácido se aplana dentro del agua | Cuarenta gotas en una columna sobre un pozo de 100x45 con la superficie en la fila 30: al paso 600 ocupan 37 columnas de ancho y 2 filas de alto, frente a las 28 y las 4 sin la nivelación; al 900 es una sola fila de 37 columnas, donde el cono sigue con 10 de ancho y 3 de alto |
| La nivelación de lado no mueve la cuenta | Las mismas 4.540 partículas de la escena en las seis medidas (pasos 200, 400, 600, 900, 1.200 y 1.600), sin una de más ni una de menos |
| El rodar no reinicia la espera | Un grano solo sobre el pozo sigue bajando una fila cada 60 pasos (fila 16 al 60, 17 al 120, 18 al 180, 19 al 240) |
| El rodar no toca lo que está seco | La misma columna de arena sobre suelo seco acaba en 19 de ancho y 8 de alto con las reglas y sin ellas |
| La madera no se empuja | Un muro de veintiuna celdas de madera con agua a los dos lados, diez fotogramas de golpe a fuerza máxima: las 21 siguen en su columna y el agua apoyada en el muro no pasa (se queda en la 29), mientras la de al lado, con sitio libre, avanza una celda por fotograma (35 → 45). Sin la excepción el muro entero se va: 0 de 21 en su columna y el agua de detrás lo sigue |

---

## 12. Trampas conocidas

Errores que ya se cometieron, con su síntoma, para no repetirlos:

| Síntoma | Causa | Regla |
|---|---|---|
| El material con vida no se gasta nunca | Su regla de envejecimiento pide algo que no tiene (hueco encima) | Toda regla que deba envejecer lleva `W`/`V`/`r`/`l` en el centro, y hay al menos una que encaja siempre |
| El fuego es infinito en cada generación | Poner una letra de material en el `result` (`?F?`) | Una letra en el `result` **crea**; si solo se quiere mantener, es `X` |
| La madera renace con la vida a tope | `M → M` | `M → X`: el material se conserva, la vida no se resetea |
| La madera se quema en 5 fotogramas | Usar `r` (mitad de la vida restante) con bandas grandes | `W` para consumir despacio, un valor por generación |
| Se pierden partículas al deslizar | Escribir el destino sin comprobar si está libre | `isFree` antes de escribir; si falla, la partícula vuelve a su sitio |
| Las partículas se duplican | Copiar a los vecinos con `X` en el `result` | Los `?` no se copian: cada partícula se mueve en su propia regla |
| Las pilas crecen torcidas | Barrido siempre en el mismo sentido | Alternar el sentido cada fotograma (`frameCounter & 1`) |
| Un patrón no coincide nunca | Olvidar que el centro del dibujo es la partícula | Ancla calculada: el carácter central tiene que ser la partícula |
| La barra inferior aparece recortada | Sumar a mano los altos de la escena | Dejar que la escena pregunte el tamaño al `root` (`new Scene(root)`) |
| El fuego se descontrola y acaba llenando el tablero | Dar a la copia de `o` una edad constante (`last - size / copies + 1`) | El hijo calcula su valor **desde el padre**: una constante le estrena el reloj y la estirpe deja de caducar nunca |
| El agua no apaga el fuego, o lo apaga perdiendo gotas | Intentar matar la llama desde el agua, escribiendo en su celda | La llama se apaga desde **su** regla (`?A? / ?F?` → `?_?`): el fuego se escribe a sí mismo al final de su paso y borraría la gota |
| La gota de agua desaparece al saltar al fuego | Salto hacia abajo o de lado | La gota solo salta hacia arriba: la llama de encima ya se barrió y su celda está libre |
| Una partícula desaparece cada vez que le toca descansar | Omitir el paso de la que no tiene el turno | La celda sin turno se copia tal cual al búfer siguiente (`nextCells[y][x] = value`) |
| Las pilas se inclinan desde que hay relojes | El sentido del barrido se alterna por paso, o el cociente entre el reloj y el multiplicador es par: todos los turnos del material caen en la misma paridad | Alternar el sentido por **fotograma** y dejar impar `reloj / multiplicador` cuando el reloj es mayor que el multiplicador (12 / 4 = 3) |
| Un material va a la mitad de velocidad de la que se le supone | Contar su reloj en fotogramas cuando el mundo da varios pasos por fotograma | El reloj de un material se cuenta en **pasos** (`stepCounter`); el fotograma es solo del golpe y del pintado |
| Al subir `ENGINE_MULTIPLIER` el chorro se abre | El pincel medido en fotogramas y el material en pasos: al subir el multiplicador el grano nace pegado al anterior | Medir el pincel en los mismos pasos que el material (`stepCounter % (stepPeriod * 2)`), así el hueco se mantiene con cualquier multiplicador |
| El chorro se abre en cono de tres columnas | El pincel suelta un grano por turno del material: nace en la celda que el anterior acaba de dejar y la generación anterior todavía lo ocupa | Gotear cada **dos** turnos del material (`stepCounter % (stepPeriod * 2)`), que deja un turno de hueco |
| Al comprimir una banda, el material se muere a media altura | Se bajó el ancho sin subir la vejez: la vida en filas es ancho × vejez | Subir la vejez en la misma proporción; el reloj no compra filas, sólo estira el tiempo |
| Al comprimir una banda, el material se vuelve más rápido | Se bajó el reloj para compensar, y el reloj mide los pasos entre turnos: bajarlo acelera | Para que queme más despacio se sube el reloj, y para que dure lo mismo se toca la vejez |
| Al subir la vejez, el material parpadea en blanco | El fantasma del último valor dura un escalón entero, no un turno | Contar con ello al elegir el ancho: con 13 turnos de vejez, el humo es casi blanco los últimos 13 de 79 |
| Una banda corta hace que `o` no cree hijos | El reparto `(last - value) / copies` da 0 cuando la banda es menor que el número de copias | El fuego necesita al menos 8 valores para propagarse con cuatro `o` |
| El ácido se queda atascado en su último tono | La dilución o la corrosión van antes que su regla de final, y `W` ni `r` pueden pasar del final de la banda | La regla de final (`c → A`) va **antes** que las cuatro diluciones y las cuatro corrosiones |
| Un charco de ácido se vuelve agua en un parpadeo | Cobrar la neutralización en el mismo paso: cada agua nueva convierte a sus vecinas y el frente avanza una celda por paso, nueve por fotograma | Cobrarla con `W` y una vejez de 60 sobre un reloj de 2: un valor cuesta sesenta turnos —ciento veinte pasos—, el frente avanza una celda cada seiscientos pasos y un charco de 30 en un pozo de 570 tarda entre 1.200 y 1.800 en ser agua |
| El ácido echado en un pozo flota y la madera del fondo no se entera | Ninguna regla de densidad: con agua debajo, `FREE_FALL` no encaja y la gota solo se abre de lado | `ACID_SINKS_IN_WATER`, un cambio de sitio `?C? / ?A?` → `?P?` que baja una fila cada dos pasos hasta el fondo |
| El ácido se mueve tan rápido como el agua | Tenerlo con reloj 1, el mismo del agua, cuando lo que se busca es un líquido pesado que se lea como tal | Reloj 2, con la vejez bajada de 120 a 60 para que la química en pasos no cambie |
| Al subir el reloj de un material, su química se hace el doble de lenta | La vejez se cuenta en turnos del material y no en pasos del mundo: con el reloj en 2 y la vejez aún en 120 un valor de dilución pasa de 120 a 240 pasos, el charco tarda en ser agua hasta el 1.800 y la madera comida al 2.400 sube de 227 a 286 | Bajar la vejez en la misma proporción en que se sube el reloj: con 2 y 60 el movimiento va a la mitad y la madera comida al 2.400 es 226, frente a 227 |
| El ácido cae dentro del agua en columnas perfectas | El único hundimiento es el cambio recto: cada gota baja por la columna en la que empezó y el pozo queda cortado por hilos de una celda de ancho | `ACID_SINKS_SIDEWAYS_IN_WATER`, el sorteo `AAA` → `ppp` (el mismo del grano de arena) delante del recto: con agua en las tres celdas de debajo la gota baja a una de ellas y la capa llega al fondo repartida |
| Las pilas dentro del agua crecen en columna | Los deslizamientos piden hueco libre (`_`) en la diagonal y dentro del agua lo que hay es agua: ninguna regla encaja y la tabla cae en `STAY` | Un cambio de sitio con la diagonal (`SAND_ROLLS_*` y `ACID_ROLLS_*`): `?X? / AXA` → `p?p`, el material baja a la diagonal y la gota sube. En el ácido va **antes** de las diluciones, que reclaman la celda aunque no les toque la vejez |
| El ácido sigue en cono dentro del agua | La nivelación de la gravedad pide hueco libre (`_`) a los lados y dentro del pozo lo que hay es agua: la gota apilada solo puede rodar a la diagonal, y un cono de cuarenta y cinco grados se sostiene solo | `ACID_LEVELS_*`: un cambio de sitio con el agua del lado (`ACA` → `p?p`) mientras lo de abajo sea ácido, que es lo que aplana el charco. Va antes de las diluciones, como el rodar |
| Un cambio de lado duplica la gota | Escribir el destino de su propia fila sin mirar si esa celda sigue con su partícula: la gota que se escurrió deja el hueco y el cambio copiaría a la que ya no está | `canGiveItsParticle`: sin leer, que esté libre; leída, sólo si su partícula sigue escrita ahí; y el valor que se lleva es el de `nextCells`, no el de `cells`, para no reiniciar la espera |
| El charco llano se acaba antes que el cono | Una lámina de una celda deja todas las gotas en contacto con el agua, y el cono protegía el interior | No es un fallo: es lo que el agua le hace a un charco de verdad, comerle la superficie. Para que dure se sube la vejez del ácido, no se quita la nivelación |
| El ácido deja de morder en cuanto entra en el agua | Las diluciones van antes que las corrosiones, y `?A? / ?C?` encaja con cualquier ácido que toque agua | Las corrosiones van **antes** que las diluciones: la gota muerde primero y solo se deja diluir cuando no tiene nada que morder |
| Se le gasta la vida a la partícula de al lado | Poner `r`/`W`/`l` en la celda de un vecino para cobrarle a él | Quien paga es quien escribe: la regla se centra en la que paga (`?C?`), no en la que se transforma |
| El agua que toca al ácido se reinicia y el grano que flota encima no se hunde | Escribir `A` en la celda de la gota vecina: una letra de material fuera de su celda toma el sitio con `spawn()` | El final escribe `A` **sólo en el centro**; las demás celdas del resultado van con `?` |
| El clic arrastra la madera | El golpe se aplica en su propio paso (`pushParticle`) y no lee la tabla, así que `WOOD_STAYS` no la protege | La excepción va en el material (`Material.ANCHORED`, hoy `"M"`) y `pushParticle` la consulta antes de tirar el dado: lo anclado no se mueve y su celda sigue ocupada, de modo que lo que se apoya en él tampoco avanza |

El número de `o` no cambia el ritmo: cada `o` tira un dado con tantas caras como
`o` hay, así que el número esperado de hijos por generación es siempre **uno**.
Lo que cambia con el número de `o` es *dónde* pueden aparecer, no cuántos. Ese
uno esperado es justo el punto crítico, y por eso una pluma de fuego se sostiene
muchas generaciones pero siempre acaba apagándose: cualquier cosa que le regale
vida (o que le reinicie el reloj a las copias) la empuja al otro lado, y
quitársela —que es lo que hace el reparto de `o`— la apaga mucho antes.

---

## 13. Pendientes e ideas

- **El calor del agua**: ya no es un pendiente. La gota que apaga una llama se
  gasta y deja humo en su sitio en las ocho formas del apagado (§8), así que el
  vapor que sube también es el del agua que apagó el fuego. Lo que sigue sin
  existir es un estado intermedio: la gota no se calienta, se convierte en humo
  en el mismo paso.
- **Densidad de verdad**: la arena se hunde en el agua al ritmo que marca la banda
  del agua y en el ácido de una sola regla, y el ácido en el agua también de tres
  —el cambio de sitio recto de `ACID_SINKS_IN_WATER`, que lo lleva al fondo de un
  pozo en una fila cada dos pasos, el sorteo de
  `ACID_SINKS_SIDEWAYS_IN_WATER`, que le deja elegir entre las tres celdas de
  debajo para no cortar el pozo en columnas, y la nivelación de lado de
  `ACID_LEVELS_*`, que es con la que deja de ser un cono—, y cuando el material se
  apila los dos ruedan a la diagonal cambiando de sitio con el agua, así que dentro
  del pozo la pila no es una columna: el grano se queda en cono, que es lo que hace
  la arena, y el ácido se aplana porque además cambia de sitio con el agua de su
  propia fila. En la madera o en el humo se queda encima: la madera no es líquida y
  el humo no tiene banda que sirva de contador. El ácido es
  el primer material que no es agua y deja pasar un grano, y lo hace porque ahí no
  hay nada que contar: se cambian de sitio y ya. Lo que falta es lo mismo al revés:
  un líquido más ligero que el agua, que flote en vez de hundirse.
- **Propagación lateral de la llama**: hoy un fuego pintado al aire se escapa
  hacia arriba y no llega a encender una pared que tenga al lado. Se arreglaría
  con una regla de fuego que no suba cuando tiene madera al lado.
- **Arnés permanente**: el arnés se ha usado y borrado cinco veces. Merece la pena
  dejarlo en el repositorio como prueba de humo.
- **El ciclo `V`**: hoy no lo usa ninguna regla (la gota que sube pasó a `B`), pero
  sigue disponible para materiales que se transforman en bucle (hielo ↔ agua, por
  ejemplo), donde interesa que el ciclo empiece siempre en el mismo valor.
- **El fantasma de las bandas cortas**: con una vejez alta, el último valor se
  queda casi blanco un escalón entero. Si molesta en pantalla, el arreglo no es
  alargar la banda: es pintar el último valor con la rampa normal y reservar el
  blanco para un valor que dure un solo turno.
- **El ácido que se vuelve agua al corroer**: implementado, y lo que lo hizo
  posible fue cobrar la neutralización (§8). Con la reacción en un paso destruía
  el material: el agua de cada bocado disparaba la del charco entero. Con la carga
  cobrada el final es una gota, el agua que deja tiene que comerse la siguiente y
  el chorro acaba entero en agua. Si se volviera a `c → H`, cada gota gastada se
  iría en humo y un charco de ácido desaparecería por arriba sin dejar una sola
  gota de agua: el final neutro es lo que hace que el ácido pueda convertirse en
  lo mismo que lo neutraliza.
- **Queda sitio**: las seis bandas ocupan 107 de los 255 valores, así que hay
  espacio para tres materiales más del tamaño del ácido sin volver a tocar
  ninguna.
