(ns quo.components.bottom-sheet.view
  (:require [reagent.core :as reagent]
            [quo.animated :as animated]
            [quo.react-native :as rn]
            [quo.react :as react]
            ["react" :as react-js]
            [quo.platform :as platform]
            [cljs-bean.core :as bean]
            [quo.components.safe-area :as safe-area]
            [quo.components.bottom-sheet.style :as styles]
            [quo.gesture-handler :as gesture-handler]))

(def opacity-range 100)

(def spring-config {:damping                   15
                    :mass                      1
                    :stiffness                 150
                    :overshootClamping         false
                    :restSpeedThreshold        0.1
                    :restDisplacementThreshold 0.1})

(defn hack-children [^js children]
  (->> children
       (react-js/Children.toArray)
       (into [])))

(defn bottom-sheet-raw [props]
  (let [{on-cancel          :onCancel
         disable-drag?      :disableDrag?
         show-handle?       :showHandle?
         visible?           :visible?
         backdrop-dismiss?  :backdropDismiss?
         back-button-cancel :backButtonCancel
         children           :children
         :or                {show-handle?       true
                             backdrop-dismiss?  true
                             back-button-cancel true}}
        (bean/bean props)

        {window-height :height} (rn/use-window-dimensions)
        safe-area               (safe-area/use-safe-area)

        visible  (react/state false)
        on-close (fn []
                   (when @visible
                     (reset! visible false)
                     (when on-cancel (on-cancel))))

        content-height       (animated/value 0)
        master-translation-y (animated/value 0)
        master-velocity-y    (animated/value (:undetermined gesture-handler/states))
        master-state         (animated/value (:undetermined gesture-handler/states))

        resistance (animated/divide master-translation-y 2)

        open       (animated/value 0)
        close      (animated/value 0)
        offset     (animated/value 0)
        clock      (animated/clock)
        body-ref   (react/create-ref)
        master-ref (react/create-ref)

        on-master-event (animated/event [{:nativeEvent
                                          {:translationY master-translation-y
                                           :state        master-state
                                           :velocityY    master-velocity-y}}])
        on-body-event   (animated/event [{:nativeEvent
                                          {:translationY master-translation-y
                                           :state        master-state
                                           :velocityY    master-velocity-y}}])

        max-height       (- window-height (:top safe-area) styles/margin-top)
        sheet-height     (animated/min* max-height content-height)
        open-snap-point  (animated/multiply -1 sheet-height)
        close-snap-point 0
        close-sheet      (fn []
                           (js/requestAnimationFrame
                            #(animated/set-value close 1)))
        on-snap          (fn [pos]
                           (when (= (aget pos 0) close-snap-point)
                             (animated/set-value offset close-snap-point)
                             (on-close)))
        translate-y      (animated/with-spring
                           {:value      resistance
                            :velocity   master-velocity-y
                            :offset     offset
                            :state      master-state
                            :config     spring-config
                            :onSnap     on-snap
                            :snapPoints [open-snap-point close-snap-point]})
        opacity          (animated/interpolate translate-y
                                               {:inputRange  [(- opacity-range) 0]
                                                :outputRange [1 0]
                                                :extrapolate (:clamp animated/extrapolate)})
        on-layout        (fn [evt]
                           (let [height (->> ^js evt
                                             .-nativeEvent
                                             .-layout
                                             .-height
                                             (+ styles/border-radius))]
                             (js/requestAnimationFrame
                              #(animated/set-value content-height height))))]
    (react/effect!
     (fn []
       (cond
         visible?
         (reset! visible visible?)

         @visible
         (close-sheet)))
     [visible?])
    (animated/code!
     (fn []
       (animated/block
        [(animated/cond*
          open
          [(animated/set offset
                         (animated/re-spring {:from   offset
                                              :to     open-snap-point
                                              :clock  clock
                                              :config spring-config}))
           (animated/cond* (animated/not* (animated/clock-running clock))
                           (animated/set open 0))])
         (animated/cond*
          close
          [(animated/set offset
                         (animated/re-timing {:from     offset
                                              :to       close-snap-point
                                              :clock    clock
                                              :duration 150}))
           (animated/cond* (animated/not* (animated/clock-running clock))
                           [(animated/call* [] on-close)
                            (animated/set close 0)])])])))
    (reagent/as-element
     [:<>
      [rn/modal {:visible                @visible
                 :transparent            true
                 :status-bar-translucent true
                 :presentation-style     :overFullScreen
                 :hardware-accelerated   true
                 :on-show                (fn []
                                           (js/requestAnimationFrame
                                            #(animated/set-value open 1)))
                 :on-request-close       (fn []
                                           (when back-button-cancel
                                             (close-sheet)))}
       [rn/view {:style          styles/container
                 :pointer-events :box-none}
        [rn/touchable-without-feedback (merge {:style styles/container}
                                              (when backdrop-dismiss?
                                                {:on-press close-sheet}))
         [animated/view {:style (merge (styles/backdrop)
                                       {:opacity opacity})}]]
        [animated/view {:style (merge (styles/content-container window-height)
                                      {:transform [{:translateY translate-y}
                                                   {:translateY (* window-height 2)}]})}
         [gesture-handler/pan-gesture-handler {:ref                  master-ref
                                               :wait-for             body-ref
                                               :enabled              (not disable-drag?)
                                               :onGestureEvent       on-master-event
                                               :onHandlerStateChange on-master-event}
          [animated/view  {:style styles/content-header}
           (when show-handle?
             [rn/view {:style styles/handle}])]]
         [gesture-handler/pan-gesture-handler {:ref                  body-ref
                                               :wait-for             master-ref
                                               :enabled              (not disable-drag?)
                                               :onGestureEvent       on-body-event
                                               :onHandlerStateChange on-body-event}
          [animated/view {:flex 1}
           [animated/scroll-view {:bounces        false
                                  :scroll-enabled true
                                  :style          {:flex 1}}
            [animated/view {:style     {:padding-top    styles/vertical-padding
                                        :padding-bottom (+ styles/vertical-padding
                                                           (:bottom safe-area))}
                            :on-layout on-layout}
             (into [:<>] (hack-children children))]]]]]]]])))

(defn bottom-sheet [props & children]
  (into [:> bottom-sheet-raw props] children))
