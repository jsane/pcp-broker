(ns puppetlabs.cthun-core
  (:require [clojure.tools.logging :as log]
            [puppetlabs.cthun.websockets :as websockets]
            [puppetlabs.cthun.connection-states :as cs]
            [puppetlabs.cthun.metrics :as metrics]
            [puppetlabs.puppetdb.mq :as mq]
            [schema.core :as s])
  (:import (org.eclipse.jetty.server Server)))

(defn- app
  [conf]
  (log/info "Metrics App initiated")
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (metrics/get-metrics-string)})

(defn make-cthun-broker
  [get-in-config inventory]
  (let [config (get-in-config [:cthun])
        host (get-in-config [:cthun :host])
        port (get-in-config [:cthun :port])
        url-prefix (get-in-config [:cthun :url-prefix])
        activemq-spool (get-in-config [:cthun :broker-spool] "tmp/activemq")
        activemq-broker (mq/build-embedded-broker activemq-spool)
        accept-consumers   (get-in-config [:cthun :accept-consumers] 4)
        delivery-consumers (get-in-config [:cthun :delivery-consumers] 16)]
    (cs/use-this-inventory inventory)
    (cs/subscribe-to-queues accept-consumers delivery-consumers)
    {:host host
     :port port
     :url-prefix url-prefix
     :config config
     :activemq-broker activemq-broker}))

(s/defn ^:always-validate stop-jetty
  [jetty-server :- Server]
  (.stop jetty-server)
  (.join jetty-server))

(defn start
  [broker]
  (let [{:keys [host port url-prefix config activemq-broker]} broker]
    (mq/start-broker! activemq-broker)
    (websockets/start-jetty app url-prefix host port config)))

(defn stop
  [{:keys [cthun jetty-server] :as service}]
  (let [{:keys [activemq-broker]} cthun]
    (mq/stop-broker! activemq-broker)
    (stop-jetty jetty-server)))

(defn state
  "Return the service state"
  [caller]
  (log/info "Not yet implemented"))
