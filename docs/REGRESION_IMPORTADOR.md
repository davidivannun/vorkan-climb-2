# Banco de regresión del importador VORKAN

Estos son casos de prueba, **no** resultados validados. La conversión debe mostrar los mismos parámetros tanto en vista previa como en el entrenamiento guiado. No inventar descansos cuando faltan.

## 1. Fuerza (abreviaturas habituales)
Entrada:
- Dominadas 4x5 · D 2'
- Flexiones 3x10 · D 90"
- Plancha 3x30" · D 60"

Esperado:
- Dominadas: modo repeticiones, series=4, repeticiones=5, descanso entre series=120 s.
- Flexiones: modo repeticiones, series=3, repeticiones=10, descanso=90 s.
- Plancha: modo tiempo, series=3, trabajo=30 s, descanso=60 s.

## 2. Resistencia
Entrada:
- Calentamiento 12'
- Escalada continua 3x5' · D 3'
- Movilidad 8'

Esperado:
- Calentamiento: 1x720 s.
- Escalada continua: 3 series de 300 s, descanso=180 s.
- Movilidad: 1x480 s.

## 3. Suspensiones
Entrada:
- Regleta 20 mm · 3 series x 6 reps de 7" ON / 3" OFF · D 3'

Esperado:
- Repeaters: regleta=20 mm, series=3, repeticiones por serie=6, ON=7 s, OFF=3 s, descanso entre series=180 s.
- El OFF entre repeticiones no debe confundirse con el descanso entre series.

## 4. Mixto con explicación
Entrada:
- Calentamiento 10 min
- Bloque 4x4 · D 2' (elige problemas de dificultad controlada)
- Dominadas 4 x 5 repeticiones con 2 min de descanso
- Plancha 3 x 30 s
- Nota: detener si aparece dolor agudo.

Esperado:
- 4 ejercicios; la nota no debe convertirse en ejercicio.
- Dominadas: 4 series de 5 y descanso 120 s.
- Plancha: 3 series de 30 s; descanso no especificado, requiere revisión en vez de inventarlo.
- Bloque 4x4: no asumir automáticamente si 2' es entre bloques o series cuando sea ambiguo.

## Criterios de aceptación
1. No perder ejercicios al dividirse un párrafo en líneas del PDF.
2. La vista previa y el guardado guiado deben derivar del mismo modelo interpretado.
3. El runner debe respetar los descansos entre series y las pausas ON/OFF.
4. Los datos ambiguos deben marcarse como pendientes; nunca rellenarse por conjetura.
5. Ejecutar estos casos en pruebas automatizadas antes de aprobar cambios del parser.
