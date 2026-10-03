import { createRouter, createWebHistory } from 'vue-router'
import CollectionBoardView from '../views/CollectionBoardView.vue'
import LabTrackView from '../views/LabTrackView.vue'
import PreparationView from '../views/PreparationView.vue'
import InventoryMatrixView from '../views/InventoryMatrixView.vue'
import CrossMatchView from '../views/CrossMatchView.vue'
import ReactionTraceView from '../views/ReactionTraceView.vue'

export const routes = [
  { path: '/', name: 'collection', component: CollectionBoardView },
  { path: '/lab', name: 'lab', component: LabTrackView },
  { path: '/preparation', name: 'preparation', component: PreparationView },
  { path: '/inventory', name: 'inventory', component: InventoryMatrixView },
  { path: '/transfusion', name: 'transfusion', component: CrossMatchView },
  { path: '/reactions', name: 'reactions', component: ReactionTraceView },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
