# Pruebas de importación con entrenamientos publicados

Fuentes externas, NO rutinas inventadas. El texto de cada caso está parafraseado en español a partir del protocolo publicado; el formato de entrada es una normalización de prueba, no una cita literal ni un PDF original.

## BMC — How to fingerboard
Fuente: https://www.thebmc.co.uk/en/how-to-fingerboard
Protocolo publicado: 7 segundos colgado, 3 segundos de descanso, 6 repeticiones; descanso 3 minutos; repetir 2 o 3 veces.
Entrada normalizada: `Suspensiones 2 series de 6 repeticiones, 7 s ON / 3 s OFF, descanso 3 min`
Esperado: 2 series, 6 repeticiones/serie, ON 7 s, OFF 3 s, descanso entre series 180 s.

## Eric Hörst — Research on Grip Strength and Hangboard Training Protocols
Fuente: https://trainingforclimbing.com/research-on-grip-strength-and-hangboard-training-protocols/
Protocolo publicado: 6 intervalos de 7 s colgado / 3 s descanso por serie; 3 min entre series; 1 a 3 series según experiencia.
Entrada normalizada: `Repeaters 3 series, 7 s ON / 3 s OFF x 6 repeticiones, descanso 3 min`
Esperado: 3 series, 6 repeticiones/serie, ON 7 s, OFF 3 s, descanso entre series 180 s.

## Gripped — Boost Your Power Endurance with Bouldering 4x4s
Fuente: https://gripped.com/indoor-climbing/boost-your-power-endurance-with-bouldering-4x4s/
Protocolo publicado: 4 bloques seguidos con poco descanso, descansar unos 4 minutos, repetir hasta completar 4 series.
Entrada normalizada: `Búlder 4x4, descanso 4 min entre series`
Esperado: 4 series de 4 bloques, descanso entre series 240 s; no inventar duración de cada bloque.

## Eric Hörst — Video: 7/53 Hangboard Training Protocol
Fuente: https://trainingforclimbing.com/video-advanced-hangboard-training-protocol/
Protocolo publicado: 3 colgadas de 7 segundos con 53 segundos entre ellas; 3–5 min entre series; 3–5 series.
Entrada normalizada: `Suspensiones 3 series x 3 repeticiones, 7 s ON / 53 s OFF, descanso 3 min entre series`
Esperado: 3 series, 3 repeticiones, ON 7 s, OFF 53 s, descanso entre series 180 s; se escoge el extremo inferior del rango publicado solo como caso de prueba.

## Criterios de aceptación
- Comprobar extracción, vista previa, conversión y temporizador por separado.
- No interpretar OFF entre repeticiones como descanso entre series.
- No inventar segundos de trabajo para bloques 4x4.
- Preservar notas del entrenador y distinguirlas de ejercicios.
- No aprobar una APK sin pruebas automatizadas y compilación verde.
