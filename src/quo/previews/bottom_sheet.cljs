(ns quo.previews.bottom-sheet
  (:require [reagent.core :as reagent]
            [quo.core :as quo]
            [quo.react-native :as rn]
            [quo.design-system.colors :as colors]
            [quo.previews.preview :as preview]))

(def descriptor [{:label "Show handle:"
                  :key   :show-handle?
                  :type  :boolean}
                 {:label "Backdrop dismiss:"
                  :key   :backdrop-dismiss?
                  :type  :boolean}
                 {:label "Disable drag:"
                  :key   :disable-drag?
                  :type  :boolean}
                 {:label "Android back cancel:"
                  :key   :back-button-cancel
                  :type  :boolean}])

(defn cool-preview []
  (let [state                 (reagent/atom {:show-handle?       true
                                             :backdrop-dismiss?  true
                                             :disable-drag?      false
                                             :back-button-cancel true})
        visible               (reagent/atom false)
        default-sheet-content (fn []
                                [rn/view {:style {:height          100
                                                  :justify-content :center
                                                  :align-items     :center}}
                                 [rn/touchable-opacity {:on-press #(reset! visible false)}
                                  [quo/text {:color :link} "Close"]]
                                 [rn/text-input {:default-value "Hello world"}]
                                 [quo/text "Hello world!"]])]
    (fn []
      [rn/view {:margin-bottom 50
                :padding       16}
       [preview/customizer state descriptor]
       [:<>
        [rn/view {:style {:align-items :center
                          :padding     16}}
         [rn/touchable-opacity {:on-press #(reset! visible true)}
          [rn/view {:style {:padding-horizontal 16
                            :padding-vertical   8
                            :border-radius      4
                            :background-color   (:interactive-01 @colors/theme)}}
           [quo/text {:color :secondary-inverse}
            (str "Open sheet" @visible)]]]]
        [quo/bottom-sheet (merge @state
                                 {:visible?  @visible
                                  :on-cancel #(reset! visible false)
                                  :content   default-sheet-content})]]])))

(defn preview []
  (fn []
    [rn/view {:flex 1 :background-color :white}
     [cool-preview]]))
