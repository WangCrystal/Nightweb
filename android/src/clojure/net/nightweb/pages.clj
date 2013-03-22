(ns net.nightweb.pages
  (:use [neko.resource :only [get-resource get-string]]
        [neko.activity :only [set-content-view!]]
        [net.clandroid.activity :only [set-state
                                       defactivity]]
        [net.clandroid.service :only [start-service
                                      stop-service
                                      start-receiver
                                      stop-receiver]]
        [net.nightweb.main :only [service-name
                                  shutdown-receiver-name]]
        [net.nightweb.views :only [create-tab
                                   get-grid-view
                                   get-user-view
                                   get-post-view
                                   get-gallery-view
                                   get-category-view]]
        [net.nightweb.menus :only [create-main-menu]]
        [net.nightweb.actions :only [show-page
                                     show-new-user-dialog
                                     show-pending-user-dialog
                                     show-welcome-dialog
                                     receive-result
                                     do-menu-action]]
        [nightweb.formats :only [base32-encode
                                 url-decode]]
        [nightweb.constants :only [my-hash-bytes]]
        [nightweb.router :only [is-first-boot?
                                user-exists?
                                user-has-content?]]))

(def show-welcome-message? true)

(defn shutdown-receiver-func
  [context intent]
  (.finish context))

(defn get-params
  [context]
  (into {} (.getSerializableExtra (.getIntent context) "params")))

(defactivity
  net.nightweb.MainPage
  :on-create
  (fn [this bundle]
    (start-service
      this
      service-name
      (fn [binder]
        (let [action-bar (.getActionBar this)]
          (.setNavigationMode action-bar 
                              android.app.ActionBar/NAVIGATION_MODE_TABS)
          (.setDisplayShowTitleEnabled action-bar false)
          (.setDisplayShowHomeEnabled action-bar false)
          (create-tab action-bar
                      (get-string :me)
                      #(let [content {:type :user :userhash my-hash-bytes}]
                         (set-state this :share content)
                         (get-user-view this content)))
          (create-tab action-bar
                      (get-string :users)
                      #(let [content {:type :user}]
                         (set-state this :share content)
                         (get-category-view this content)))
          (create-tab action-bar
                      (get-string :posts)
                      #(let [content {:type :post}]
                         (set-state this :share content)
                         (get-category-view this content)))
          (when (and is-first-boot? show-welcome-message?)
            (def show-welcome-message? false)
            (show-welcome-dialog this)))))
    (start-receiver this shutdown-receiver-name shutdown-receiver-func))
  :on-destroy
  (fn [this]
    (stop-receiver this shutdown-receiver-name)
    (stop-service this))
  :on-create-options-menu
  (fn [this menu]
    (create-main-menu this menu true))
  :on-activity-result
  receive-result)

(defactivity
  net.nightweb.CategoryPage
  :on-create
  (fn [this bundle]
    (start-receiver this shutdown-receiver-name shutdown-receiver-func)
    (let [params (get-params this)
          action-bar (.getActionBar this)]
      (.setNavigationMode action-bar android.app.ActionBar/NAVIGATION_MODE_TABS)
      (.setDisplayHomeAsUpEnabled action-bar true)
      (.setTitle action-bar (get params :title))
      (create-tab action-bar
                  (get-string :users)
                  #(get-category-view this (assoc params :subtype :user)))
      (create-tab action-bar
                  (get-string :posts)
                  #(get-category-view this (assoc params :subtype :post)))))
  :on-destroy
  (fn [this]
    (stop-receiver this shutdown-receiver-name))
  :on-create-options-menu
  (fn [this menu]
    (create-main-menu this menu false))
  :on-options-item-selected
  (fn [this item]
    (do-menu-action this item))
  :on-activity-result
  receive-result)

(defactivity
  net.nightweb.GalleryPage
  :def gallery-page
  :on-create
  (fn [this bundle]
    (start-receiver this shutdown-receiver-name shutdown-receiver-func)
    (let [params (get-params this)
          action-bar (.getActionBar this)
          view (get-gallery-view this params)]
      (.hide action-bar)
      (set-content-view! gallery-page view)))
  :on-destroy
  (fn [this]
    (stop-receiver this shutdown-receiver-name))
  :on-activity-result
  receive-result)

(defactivity
  net.nightweb.BasicPage
  :def basic-page
  :on-create
  (fn [this bundle]
    (start-service
      this
      service-name
      (fn [binder]
        (let [params (if-let [url (.getDataString (.getIntent this))]
                       (url-decode url)
                       (get-params this))
              view (case (get params :type)
                     :user (if (get params :userhash)
                             (get-user-view this params)
                             (get-category-view this params))
                     :post (if (get params :time)
                             (get-post-view this params)
                             (get-category-view this params))
                     (get-grid-view this []))
              action-bar (.getActionBar this)]
          (set-state this :share params)
          (.setDisplayHomeAsUpEnabled action-bar true)
          (if-let [title (get params :title)]
            (.setTitle action-bar title)
            (.setDisplayShowTitleEnabled action-bar false))
          (set-content-view! basic-page view)
          (if-not (user-exists? (get params :userhash))
            (show-new-user-dialog this params)
            (when-not (user-has-content? (get params :userhash))
              (show-pending-user-dialog this))))))
    (start-receiver this shutdown-receiver-name shutdown-receiver-func))
  :on-destroy
  (fn [this]
    (stop-receiver this shutdown-receiver-name)
    (stop-service this))
  :on-create-options-menu
  (fn [this menu]
    (create-main-menu this menu true))
  :on-options-item-selected
  (fn [this item]
    (do-menu-action this item))
  :on-activity-result
  receive-result)
