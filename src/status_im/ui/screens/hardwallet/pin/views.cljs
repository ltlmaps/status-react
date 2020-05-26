(ns status-im.ui.screens.hardwallet.pin.views
  (:require-macros [status-im.utils.views :refer [defview letsubs]])
  (:require [re-frame.core :as re-frame]
            [status-im.i18n :as i18n]
            [status-im.ui.components.colors :as colors]
            [status-im.ui.components.icons.vector-icons :as vector-icons]
            [status-im.ui.components.react :as react]
            [status-im.ui.screens.hardwallet.pin.styles :as styles]
            [status-im.ui.components.checkbox.view :as checkbox]
            [status-im.utils.platform :as platform]
            [status-im.ui.components.topbar :as topbar]))

(def default-pin-retries-number 3)

(defn numpad-button [n step enabled? small-screen?]
  [react/touchable-highlight
   {:on-press #(when enabled?
                 (re-frame/dispatch [:hardwallet.ui/pin-numpad-button-pressed n step]))}
   [react/view (styles/numpad-button small-screen?)
    [react/text {:style styles/numpad-button-text}
     n]]])

(defn numpad-row [[a b c] step enabled? small-screen?]
  [react/view (styles/numpad-row-container small-screen?)
   [numpad-button a step enabled? small-screen?]
   [numpad-button b step enabled? small-screen?]
   [numpad-button c step enabled? small-screen?]])

(defn numpad [step enabled? small-screen?]
  [react/view styles/numpad-container
   [numpad-row [1 2 3] step enabled? small-screen?]
   [numpad-row [4 5 6] step enabled? small-screen?]
   [numpad-row [7 8 9] step enabled? small-screen?]
   [react/view (styles/numpad-row-container small-screen?)
    [react/view (styles/numpad-empty-button small-screen?)]
    [numpad-button 0 step enabled? small-screen?]
    [react/touchable-highlight
     {:on-press #(when enabled?
                   (re-frame/dispatch [:hardwallet.ui/pin-numpad-delete-button-pressed step]))}
     [react/view (styles/numpad-delete-button small-screen?)
      [vector-icons/icon :main-icons/backspace {:color colors/blue}]]]]])

(defn pin-indicator [pressed? status]
  [react/view (styles/pin-indicator pressed? status)])

(defn pin-indicators [pin status group-size style]
  [react/view (merge styles/pin-indicator-container style)
   (map-indexed
    (fn [i n]
      ^{:key i}
      [pin-indicator (number? n) status])
    (concat pin (repeat (- group-size (count pin)) nil)))])

(defn puk-indicators [puk status]
  [react/view {:margin-top 28
               :flex-direction :row
               :justify-content :space-between}
   (map-indexed
    (fn [i puk-group]
      ^{:key i}
      [pin-indicators puk-group status 4 {:margin-top 8 :margin 12}])
    (partition 4
               (concat puk
                       (repeat (- 12 (count puk))
                               nil))))])

(defn save-password []
  (let [{:keys [save-password?]} @(re-frame/subscribe [:multiaccounts/login])
        auth-method @(re-frame/subscribe [:auth-method])]
    (when-not (and platform/android? (not auth-method))
      [react/view
       {:style {:flex-direction :row}}
       [checkbox/checkbox
        {:checked?        save-password?
         :style           {:margin-right 10}
         :on-value-change #(re-frame/dispatch [:multiaccounts/save-password %])}]
       [react/text (i18n/label :t/hardwallet-dont-ask-card)]])))

(defn pin-view
  [{:keys [pin title-label description-label step status error-label
           retry-counter small-screen? save-password-checkbox?]}]
  (let [enabled? (not= status :verifying)]
    [react/scroll-view
     [react/view styles/pin-container
      [react/view (styles/center-container title-label)
       (when title-label
         [react/text {:style styles/center-title-text}
          (i18n/label title-label)])
       (when description-label
         [react/text {:style           styles/create-pin-text
                      :number-of-lines 2}
          (i18n/label description-label)])
       (when save-password-checkbox?
         [save-password])
       [react/view {:flex 1}
        (case status
          :verifying [react/view styles/waiting-indicator-container
                      [react/activity-indicator {:animating true
                                                 :size      :small}]]
          :error [react/view (styles/error-container small-screen?)
                  [react/text {:style (styles/error-text small-screen?)}
                   (i18n/label error-label)]]
          (when (and retry-counter (< retry-counter default-pin-retries-number))
            [react/view {:margin-top (if (= step :puk) 24 8)}
             (case retry-counter
               2 [react/text {:style {:text-align :center
                                      :color colors/gray}}
                  (i18n/label :t/pin-two-attempts-left)]
               1 [react/nested-text {:style {:text-align :center
                                             :color colors/gray}}
                  (i18n/label :t/pin-one-attempt-left-part-one)
                  [{:color colors/black
                    :font-weight "700"}
                   (i18n/label :t/pin-one-attempt-left-part-two)]
                  (i18n/label :t/pin-one-attempt-left-part-three)])]))]

       (if (= step :puk)
         [puk-indicators pin status]
         [pin-indicators pin status 6 nil])
       [numpad step enabled? small-screen?]]]]))

(def pin-retries 3)
(def puk-retries 5)

(defview enter-pin []
  (letsubs [pin [:hardwallet/pin]
            step [:hardwallet/pin-enter-step]
            status [:hardwallet/pin-status]
            pin-retry-counter [:hardwallet/pin-retry-counter]
            puk-retry-counter [:hardwallet/puk-retry-counter]
            error-label [:hardwallet/pin-error-label]]
    [react/view {:flex             1
                 :background-color colors/white}
     [topbar/topbar {}]
     (if (zero? pin-retry-counter)
       [pin-view {:pin               pin
                  :retry-counter     (when (< puk-retry-counter puk-retries) puk-retry-counter)
                  :title-label       :t/enter-puk-code
                  :description-label :t/enter-puk-code-description
                  :step              step
                  :status            status
                  :error-label       error-label}]
       [pin-view {:pin               pin
                  :retry-counter     (when (< pin-retry-counter pin-retries) pin-retry-counter)
                  :title-label       (case step
                                       :current :t/current-pin
                                       :login :t/current-pin
                                       :import-multiaccount :t/current-pin
                                       :original :t/create-a-pin
                                       :confirmation :t/repeat-pin
                                       :t/current-pin)
                  :description-label (case step
                                       :current :t/current-pin-description
                                       :sign :t/current-pin-description
                                       :import-multiaccount :t/current-pin-description
                                       :login :t/login-pin-description
                                       :t/new-pin-description)
                  :step              step
                  :status            status
                  :error-label       error-label}])]))
