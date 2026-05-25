# ChatRoom - Android Application

ChatRoom es una aplicación de mensajería instantánea para Android que permite a los usuarios unirse a salas de chat grupales mediante escaneo de códigos QR y mantener conversaciones privadas en tiempo real.

## 🚀 Características Principales

### 🔐 Autenticación y Seguridad
* **Registro de Usuarios**: Creación de cuentas con validación de correo electrónico.
* **Inicio de Sesión**: Soporte para login tradicional y **Google Sign-In**.
* **Verificación de Email**: Sistema de código de seguridad para activar cuentas.
* **Gestión de Perfil**: Actualización de datos personales, foto de perfil y contraseña.

### 🏠 Salas de Chat (Grupos)
* **Unión vía QR**: Sistema integrado de escaneo de códigos QR (usando Google ML Kit) para entrar a salas.
* **Geofencing**: Verificación de ubicación (GPS) para asegurar que el usuario esté dentro del radio permitido de la sala.
* **Administración**: Sistema de roles, expulsiones temporales con motivo y gestión de usuarios.

### 💬 Chat Privado
* **Mensajería 1 a 1**: Conversaciones privadas entre usuarios.
* **Estado en Tiempo Real**: Indicador de "escribiendo..." y notificaciones de mensajes no leídos.
* **Seguridad en Privados**: Opción de reportar usuarios y finalizar chats vinculados a salas específicas.

### 🛠️ Interfaz y Experiencia de Usuario
* **Modo Oscuro/Claro**: Soporte completo para temas del sistema.
* **Diseño Moderno**: Uso de Material Design 3, animaciones Lottie y efectos de carga Shimmer.
* **Multimedia**: Gestión de imágenes de perfil con Glide y CircleImageView.

## 🛠️ Stack Tecnológico

* **Lenguaje**: Java
* **Arquitectura**: Basada en Actividades y Servicios REST.
* **Network**: Retrofit 2 & OkHttp 3 para comunicación con la API.
* **Visión Artificial**: Google ML Kit para escaneo de códigos de barras/QR.
* **Localización**: Google Play Services Location para validación de salas.
* **UI/UX**: 
    - Lottie (Animaciones)
    - Glide (Carga de imágenes)
    - Facebook Shimmer (Efectos de carga)
    - Toasty (Notificaciones visuales personalizadas)

## 📂 Estructura del Proyecto

```text
com.example.chat
├── activities      # Actividades principales (Login, Register, Home, Chat, etc.)
├── adapters        # Adaptadores para RecyclerView (Mensajes, Salas, Usuarios)
├── models          # Modelos de datos (Mensaje, Sala, Usuario)
├── network         # Configuración de Retrofit y servicios API
└── utils           # Clases de apoyo y utilidades
```

## ⚙️ Configuración del Entorno

1.  **Clonar el repositorio**:
    ```bash
    git clone https://github.com/tu-usuario/ChatRoomAndroid.git
    ```
2.  **Abrir en Android Studio**: Importar el proyecto como un proyecto Gradle existente.
3.  **Configurar la API**: 
    - Asegúrate de que el servidor backend esté en ejecución.
    - Modifica la URL base en `com.example.chat.network.RetrofitClient`.
4.  **Google Services**:
    - Para el login con Google, es necesario configurar el archivo `google-services.json` en la carpeta `app/`.

## 📸 Capturas de Pantalla
*(Próximamente)*

---
Desarrollado como proyecto de mensajería avanzada para Android.