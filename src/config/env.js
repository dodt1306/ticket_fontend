export const ENV = {
  API_BASE: import.meta.env.VITE_API_BASE,
  MQTT_URL: import.meta.env.VITE_MQTT_URL,
  APP_ENV: import.meta.env.VITE_APP_ENV,
  IS_DEV: import.meta.env.VITE_APP_ENV === "dev",
};
