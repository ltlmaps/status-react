(ns status-im.ui.screens.chat.toolbar-content
  (:require [status-im.i18n :as i18n]
            [status-im.ui.components.chat-icon.screen :as chat-icon.screen]
            [status-im.ui.components.react :as react]
            [status-im.ui.screens.chat.styles.main :as st])
  (:require-macros [status-im.utils.views :refer [defview letsubs]]))

(defn- in-progress-text [{:keys [highestBlock currentBlock startBlock]}]
  (let [total      (- highestBlock startBlock)
        ready      (- currentBlock startBlock)
        percentage (if (zero? ready)
                     0
                     (->> (/ ready total)
                          (* 100)
                          (.round js/Math)))]

    (str (i18n/label :t/sync-in-progress) " " percentage "% " currentBlock)))

(defn- group-last-activity [{:keys [contacts public?]}]
  [react/view {:flex-direction :row}
   [react/text {:style st/toolbar-subtitle}
    (if public?
      (i18n/label :t/public-group-status)
      (let [cnt (count contacts)]
        (if (zero? cnt)
          (i18n/label :members-active-none)
          (i18n/label-pluralize cnt :t/members-active))))]])

(defn- contact-indicator [{:keys [added?]}]
  [react/view {:flex-direction :row}
   [react/text {:style st/toolbar-subtitle}
    (if added?
      (i18n/label :chat-is-a-contact)
      (i18n/label :chat-is-not-a-contact))]])

(defview toolbar-content-view []
  (letsubs [{:keys [group-chat
                    color
                    contacts
                    chat-name
                    contact
                    public?]}
            [:chats/current-chat]]
    [react/view {:style st/toolbar-container}
     [react/view {:margin-right 10}
      [chat-icon.screen/chat-icon-view-toolbar contact group-chat chat-name color]]
     [react/view {:style st/chat-name-view}
      [react/text {:style               st/chat-name-text
                   :number-of-lines     1
                   :accessibility-label :chat-name-text}
       chat-name]
      (when contact
        [contact-indicator contact])
      (if group-chat
        [group-last-activity {:contacts   contacts
                              :public?    public?}])]]))
