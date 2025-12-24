import { createContext, useContext, useEffect, useRef, useState } from "react";
import mqtt from "mqtt";
import { ENV } from "../config/env";
const MQTT_URL = `${ENV.MQTT_URL}`;
const MQTTContext = createContext(null);

export function MQTTProvider({ visitorToken, children }) {
  const clientRef = useRef(null);
  const [client, setClient] = useState(null); // 🔑 TRIGGER RERENDER

  // CONNECT
  useEffect(() => {
    if (!visitorToken) return;
    if (clientRef.current) return;

    const c = mqtt.connect(MQTT_URL, {
      clientId: visitorToken,
      clean: true,
    });

    clientRef.current = c;
    setClient(c); // 🔑 publish client cho consumers

    c.on("connect", () => {
      console.log("[MQTT] connected", visitorToken);
      c.subscribe(`visitor/${visitorToken}`);
    });

    c.on("error", err => {
      console.error("[MQTT] error", err);
    });
  }, [visitorToken]);

  // DISCONNECT only on unmount
  useEffect(() => {
    return () => {
      if (clientRef.current) {
        clientRef.current.end(true);
        clientRef.current = null;
        setClient(null);
      }
    };
  }, []);

  return (
    <MQTTContext.Provider value={client}>
      {children}
    </MQTTContext.Provider>
  );
}

export function useMQTTClient() {
  return useContext(MQTTContext);
}
