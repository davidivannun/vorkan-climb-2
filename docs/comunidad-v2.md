# Comunidad compartida: diseño inicial

La APK actual es **local**. Estos archivos preparan el futuro backend, pero no activan sincronización ni cuentas por sí solos.

## Estructura Firestore
- `users/{uid}`: perfil privado del usuario.
- `gyms/{gymId}`: ficha pública buscable por nombre y ciudad; creador `ownerId`.
- `gyms/{gymId}/requests/{uid}`: solicitud de entrada pendiente.
- `gyms/{gymId}/members/{uid}`: miembro aprobado, rol `member` o `admin`.
- `gyms/{gymId}/blocks/{blockId}`: bloque publicado, inmutable.
- Imagen: `gyms/{gymId}/blocks/{blockId}.jpg` en almacenamiento de objetos.

## Reglas funcionales
1. Cualquiera con cuenta puede crear un rocódromo y buscar su ficha pública.
2. Los bloques solo son visibles para miembros y administradores.
3. Solo el administrador acepta solicitudes y añade miembros.
4. Cada miembro crea sus propios bloques, sin poder editarlos después.
5. El administrador o autor puede retirar bloques.
6. Cada bloque mantiene foto propia, sin clasificar por paredes.

## Antes de conectar móviles
- Crear proyecto backend y configurar autenticación.
- Añadir reglas de **Storage** equivalentes a las de Firestore; nunca dejar el bucket público.
- Añadir validaciones y límites de tamaño/formatos de fotografías; contemplar cuotas, facturación y abuso.
- Probar las reglas con emuladores, incluidas consultas y acceso denegado.
- Configurar búsqueda indexada por nombre/ciudad y flujo de aprobación de solicitudes.
- Añadir controles contra solicitudes repetidas, spam y cuentas abusivas.
- Separar credenciales de desarrollo y producción.

**Importante:** las reglas de Firestore son un borrador técnico. No desplegarlas sin pruebas de seguridad.
