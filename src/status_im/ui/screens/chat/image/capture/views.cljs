(ns status-im.ui.screens.chat.image.capture.views
  (:require [status-im.ui.components.react :as react]
            [re-frame.core :as re-frame]
            [status-im.ui.components.colors :as colors]
            [taoensso.timbre :as log]
            [status-im.ui.components.icons.vector-icons :as icons]
            [status-im.ui.components.camera :as camera]
            [reagent.core :as reagent]
            [status-im.utils.utils :as utils]
            [status-im.i18n :as i18n]
            [status-im.utils.platform :as platform]))

(defn image-captured [^js data]
  (re-frame/dispatch [:chat.ui/image-captured (.-uri data)])
  (re-frame/dispatch [:navigate-back]))

(defn camera-permissions []
  [react/view {:flex 1 :justify-content :center :align-items :center}
   [react/view {:width       128 :height 208 :background-color colors/black-transparent-86 :border-radius 4
                :align-items :center :justify-content :center}
    [icons/icon :camera-permission {:color colors/gray}]
    [react/text {:style {:margin-top        8 :color colors/gray :font-size 12
                         :margin-horizontal 8 :text-align :center}}
     (i18n/label :t/give-permissions-camera)]]])

(defn camera-permissions-fn [obj]
  (when-not (= "READY" (.-status obj))
    (reagent/as-element [camera-permissions])))

(defn capture-image []
  (let [camera-ref   (atom nil)
        focus-object (reagent/atom nil)
        layout       (atom nil)
        front?       (reagent/atom false)]
    (fn []
      [react/view {:flex 1}
       [camera/camera
        {:style                        {:flex 1}
         :captureQuality               "480p"
         :type                         (if @front? "front" "back")
         :ref                          #(reset! camera-ref %)
         :on-layout                    (camera/on-layout layout)
         :auto-focus-point-of-interest @focus-object
         :on-tap                       (camera/on-tap camera-ref layout focus-object)
         :captureAudio                 false}
        camera-permissions-fn]
       [react/view {:position :absolute :bottom 0 :left 0 :right 0}
        [react/safe-area-view {:style {:flex 1 :justify-content :flex-end}}
         [react/view {:flex-direction :row :justify-content :space-between :align-items :center
                      :padding        16}
          [react/touchable-highlight
           {:on-press #(swap! front? not)}
           [react/view {:width            48 :height 48 :border-radius 44
                        :background-color colors/black-transparent-86
                        :align-items      :center :justify-content :center}
            [icons/icon :rotate-camera {:color colors/white-persist}]]]
          [react/touchable-highlight
           {:on-press (fn []
                        (let [^js camera @camera-ref]
                          (-> (.takePictureAsync camera)
                              (.then image-captured)
                              (.catch #(log/debug "Error capturing image: " %)))))}
           [react/view {:width            73 :height 73 :border-radius 70
                        :background-color colors/black-transparent-86
                        :border-width     4 :border-color colors/white-persist}]]
          ;;TODO implement
          [react/view {:width 48 :height 48}]
          #_[react/view {:width            48 :height 48 :border-radius 44
                         :background-color colors/black-transparent-86
                         :align-items      :center :justify-content :center}
             [icons/icon :flash {:color colors/white}]]]
         [react/touchable-highlight
          {:style    {:align-self    :center
                      :margin-bottom 20}
           :on-press #(re-frame/dispatch [:navigate-back])}
          [react/view {:width            90 :height 40 :border-radius 44
                       :background-color colors/black-transparent-86
                       :align-items      :center :justify-content :center}
           [react/text {:style {:color       colors/white-persist
                                :font-weight "500"}}
            (i18n/label :t/close)]]]]]])))

(defn take-picture []
  (re-frame/dispatch
   [:request-permissions
    {:permissions [:camera]
     :on-allowed  #(re-frame/dispatch [:navigate-to :capture-image])
     :on-denied   (fn []
                    (utils/set-timeout
                     #(utils/show-popup (i18n/label :t/error)
                                        (i18n/label :t/camera-access-error))
                     50))}]))

(defn camera-button []
  [react/touchable-without-feedback {:on-press take-picture :style {:flex 1}}
   [react/view {:style {:width            128 :flex 1
                        :border-radius    4 :overflow :hidden
                        :background-color colors/black-persist}}
    (if platform/android?
      ;;TODO on Android camera is slow and touchable doesn't work for it, so show camera only on ios
      [react/view {:flex 1 :justify-content :center :align-items :center}
       [icons/icon :camera-permission {:color colors/gray}]]
      [camera/camera {:style        {:flex 1}
                      :captureAudio false}
       camera-permissions-fn])]])